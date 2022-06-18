/*
 *  Copyright 2006-2021 WebPKI.org (http://webpki.org).
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.webpki.cbor;

import java.io.IOException;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

import java.security.cert.X509Certificate;

import java.util.ArrayList;

import org.webpki.crypto.AsymSignatureAlgorithms;
import org.webpki.crypto.CertificateUtil;
import org.webpki.crypto.ContentEncryptionAlgorithms;
import org.webpki.crypto.CryptoRandom;
import org.webpki.crypto.EncryptionCore;
import org.webpki.crypto.KeyAlgorithms;
import org.webpki.crypto.KeyEncryptionAlgorithms;
import org.webpki.crypto.SignatureWrapper;

import static org.webpki.cbor.CBORCryptoConstants.*;

/**
 * Class holding crypto support 
 */
public class CBORCryptoUtils {
    
    CBORCryptoUtils() {}
    
    /**
     * Decodes a certificate path from a CBOR array.
 
     * Note that the array must only contain a
     * list of X509 certificates in DER format,
     * each encoded as a CBOR <code>byte&nbsp;string</code>. 
     * The certificates must be in ascending
     * order with respect to parenthood.  That is,
     * the first certificate would typically be
     * an end-entity certificate.
     * 
     * See {@link #encodeCertificateArray(X509Certificate[])}.
     * 
     * @return Certificate path
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public static X509Certificate[] decodeCertificateArray(CBORArray array) 
            throws IOException, GeneralSecurityException {
        ArrayList<byte[]> blobs = new ArrayList<>();
        int index = 0;
        do {
            blobs.add(array.objectList.get(index).getByteString());
        } while (++index < array.objectList.size());
        return CertificateUtil.makeCertificatePath(blobs);
    }

    /**
     * Encodes certificate path into a CBOR array.
 
     * Note that the certificates must be in ascending
     * order with respect to parenthood.  That is,
     * the first certificate would typically be
     * an end-entity certificate.  The CBOR array
     * will after the conversion hold a list of
     * DER-encoded certificates, each represented by a CBOR
     * <code>byte&nbsp;string</code>.
     * 
     * See  {@link #decodeCertificateArray(CBORArray)}.
     * 
     * @param certificatePath The certificate path to be converted to CBOR 
     * 
     * @return CBORArray
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public static CBORArray encodeCertificateArray(X509Certificate[] certificatePath) 
            throws IOException, GeneralSecurityException {
        CBORArray array = new CBORArray();
        for (X509Certificate certificate : CertificateUtil.checkCertificatePath(certificatePath)) {
            array.addObject(new CBORByteString(certificate.getEncoded()));
        }
        return array;
    }

    /**
     * Retrieves the container map object.
 
     * <p>
     * This method is intended for CBOR map objects that <i>optionally</i>
     * are wrapped in a tag.  This implementation accepts two variants of tags:
     * </p>
     * <pre>
     *     nnn({})
     *     nnn(["string data", {}])
     * </pre>
     * 
     * @param container A map optionally enclosed in a tag 
     * @return The map object without the tag
     * @throws IOException
     */
    public static CBORMap getContainerMap(CBORObject container) throws IOException {
        if (container.getType() == CBORTypes.TAGGED_OBJECT) {
            CBORObject tagged = container.getTaggedObject(container.getTagNumber());
            if (tagged.getType() == CBORTypes.ARRAY) {
                CBORArray holder = tagged.getArray();
                if (holder.size() != 2 ||
                    holder.getObject(0).getType() != CBORTypes.TEXT_STRING) {
                    throw new IOException(
                            "Tag syntax nnn([\"string\", {}]) expected");
                }
                container = holder.getObject(1);
            } else container = tagged;
        }
        return container.getMap();
    }
    
    static byte[] setupBasicKeyEncryption(PublicKey publicKey,
                                          CBORMap keyEncryption,
                                          KeyEncryptionAlgorithms keyEncryptionAlgorithm,
                                          ContentEncryptionAlgorithms contentEncryptionAlgorithm) 
            throws GeneralSecurityException, IOException {

        // The mandatory key encryption algorithm
        keyEncryption.setObject(ALGORITHM_LABEL,
                                new CBORInteger(keyEncryptionAlgorithm.getCoseAlgorithmId()));
        
        // Key wrapping algorithms need a key to wrap
        byte[] contentEncryptionKey = keyEncryptionAlgorithm.isKeyWrap() ?
            CryptoRandom.generateRandom(contentEncryptionAlgorithm.getKeyLength()) : null;
                                                                         
        // The real stuff...
        EncryptionCore.AsymmetricEncryptionResult asymmetricEncryptionResult =
                keyEncryptionAlgorithm.isRsa() ?
                    EncryptionCore.rsaEncryptKey(contentEncryptionKey,
                                                 keyEncryptionAlgorithm,
                                                 publicKey)
                                               :
                    EncryptionCore.senderKeyAgreement(true,
                                                      contentEncryptionKey,
                                                      keyEncryptionAlgorithm,
                                                      contentEncryptionAlgorithm,
                                                      publicKey);
        if (!keyEncryptionAlgorithm.isRsa()) {
            // ECDH-ES requires the ephemeral public key
            keyEncryption.setObject(EPHEMERAL_KEY_LABEL,
                                    CBORPublicKey.encode(
                                        asymmetricEncryptionResult.getEphemeralKey()));
        }
        if (keyEncryptionAlgorithm.isKeyWrap()) {
            // Encrypted key
            keyEncryption.setObject(CIPHER_TEXT_LABEL,
                                    new CBORByteString(
                                        asymmetricEncryptionResult.getEncryptedKeyData()));
        }
        return asymmetricEncryptionResult.getContentEncryptionKey();
    }
    
    static byte[] asymKeyDecrypt(PrivateKey privateKey,
                                 CBORMap innerObject,
                                 KeyEncryptionAlgorithms keyEncryptionAlgorithm,
                                 ContentEncryptionAlgorithms contentEncryptionAlgorithm)
            throws GeneralSecurityException, IOException {

        // Fetch ephemeral key if applicable
        PublicKey ephemeralKey = null;
        if (!keyEncryptionAlgorithm.isRsa()) {
            ephemeralKey = CBORPublicKey.decode(innerObject.getObject(EPHEMERAL_KEY_LABEL));
        }
        
        // Fetch encrypted key if applicable
        byte[] encryptedKey = null;
        if (keyEncryptionAlgorithm.isKeyWrap()) {
            encryptedKey = innerObject.getObject(CIPHER_TEXT_LABEL).getByteString();
        }
        return keyEncryptionAlgorithm.isRsa() ?
            EncryptionCore.rsaDecryptKey(keyEncryptionAlgorithm, 
                                         encryptedKey,
                                         privateKey)
                                              :
            EncryptionCore.receiverKeyAgreement(true,
                                                keyEncryptionAlgorithm,
                                                contentEncryptionAlgorithm,
                                                ephemeralKey,
                                                privateKey,
                                                encryptedKey);
    }

    static void asymKeySignatureValidation(PublicKey publicKey,
                                           AsymSignatureAlgorithms signatureAlgorithm,
                                           byte[] signedData,
                                           byte[] signatureValue) 
            throws GeneralSecurityException, IOException {

        // Verify that the public key matches the signature algorithm.
        KeyAlgorithms keyAlgorithm = KeyAlgorithms.getKeyAlgorithm(publicKey);
        if (signatureAlgorithm.getKeyType() != keyAlgorithm.getKeyType()) {
            throw new GeneralSecurityException("Algorithm " + signatureAlgorithm + 
                                               " does not match key type " + keyAlgorithm);
        }
        
        // Finally, verify the signature.
        if (!new SignatureWrapper(signatureAlgorithm, publicKey)
                 .update(signedData)
                 .verify(signatureValue)) {
            throw new GeneralSecurityException("Bad signature for key: " + publicKey.toString());
        }
    }
   
    static void rejectPossibleKeyId(CBORObject optionalKeyId) throws GeneralSecurityException {
        if (optionalKeyId != null) {
            throw new GeneralSecurityException(STDERR_KEY_ID_PUBLIC);
        }
    }
    
    /**
     * For internal use only
     */
    static final String STDERR_KEY_ID_PUBLIC = 
            "\"keyId\" cannot be combined with public key objects";

}
