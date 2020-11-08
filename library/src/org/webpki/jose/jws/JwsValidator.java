/*
 *  Copyright 2018-2020 WebPKI.org (http://webpki.org).
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
package org.webpki.jose.jws;

import java.io.IOException;

import java.security.GeneralSecurityException;

import org.webpki.util.Base64URL;

/**
 * JWS validator base class
 */
public abstract class JwsValidator {
    
    String provider;

    JwsValidator() {}
    
    abstract void validate(byte[] signedData, JwsDecoder jwsDecoder) 
            throws IOException, GeneralSecurityException;

    /**
     * Set cryptographic provider
     * @param provider Name of provider like "BC"
     * @return this
     */
    public JwsValidator setProvider(String provider) {
        this.provider = provider;
        return this;
    }
    
    /**
     * Validate compact JWS signature in "detached" mode
     * @param jwsDecoder Decoded JWS data
     * @param detachedPayload Detached payload
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public void validateDetachedSignature(JwsDecoder jwsDecoder, byte[] detachedPayload) 
            throws IOException, GeneralSecurityException {

        // Dealing with detached signatures
        if (jwsDecoder.jwsPayloadB64U == null) {
            if (detachedPayload == null) {
                throw new IllegalArgumentException("Detached payload missing");
            }
            jwsDecoder.jwsPayloadB64U = Base64URL.encode(detachedPayload);
        } else {
            throw new IllegalArgumentException("Both external and JWS-supplied payload?");
        }
        
        // Main JWS validator
        validateSignature(jwsDecoder);
    }

    /**
     * Validate compact JWS signature in "standard" mode
     * @param jwsDecoder Decoded JWS data
     * @throws IOException
     * @throws GeneralSecurityException
     */
    public void validateSignature(JwsDecoder jwsDecoder) 
            throws IOException, GeneralSecurityException {

        // Dealing with in-line signatures
        if (jwsDecoder.jwsPayloadB64U == null) {
            throw new IllegalArgumentException("Missing payload, detached mode?");
        }
        
        // Delegated validation
        validate((jwsDecoder.jwsProtectedHeaderB64U + 
                    "." + 
                    jwsDecoder.jwsPayloadB64U).getBytes("utf-8"),
                 jwsDecoder);
        
        // No access to payload without having passed validation
        jwsDecoder.validated = true;
    }
}
