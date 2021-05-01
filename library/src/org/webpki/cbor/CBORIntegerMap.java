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

/**
 * Class for holding CBOR integer maps.
 */
public class CBORIntegerMap extends CBORMapBase {

    public CBORIntegerMap() {}

    public CBORIntegerMap setMappedValue(int key, CBORObject cborObject) throws IOException {
        setObject(new CBORInteger(key), cborObject);
        return this;
    }

    public CBORObject getMappedValue(int key) throws IOException {
        return getObject(new CBORInteger(key));
    }
}
