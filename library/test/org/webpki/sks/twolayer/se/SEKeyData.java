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
package org.webpki.sks.twolayer.se;

import java.security.PublicKey;

public class SEKeyData extends SEResult {
    
    byte[] provisioningState;

    public byte[] getProvisioningState() {
        testReturn();
        return provisioningState;
    }
    
    byte[] sealedKey;
    
    public byte[] getSealedKey() {
        return sealedKey;
    }
    
    byte[] attestation;
    
    public byte[] getAttestation() {
        return attestation;
    }

    byte[] decryptedPinValue;
    
    public byte[] getDecryptedPinValue() {
        return decryptedPinValue;
    }

    PublicKey publicKey;
    
    public PublicKey getPublicKey() {
        return publicKey;
    }
}
