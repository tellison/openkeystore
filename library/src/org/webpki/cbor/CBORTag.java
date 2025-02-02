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

import org.webpki.util.ArrayUtil;

/**
 * Class for holding CBOR tagged objects.
 * <p>
 * Tagged objects are based on CBOR major type 6.
 * This implementation accepts two variants of tags:
 * </p>
 * <div style='margin-left:4em'>
 * <code>nnn(</code><i>CBOR&nbsp;object&nbsp;</i><code>)</code><br>
 * <code>{@value #RESERVED_TAG_COTX}([</code><i>CBOR&nbsp;text&nbsp;string</i><code>,
 * </code><i>CBOR&nbsp;object&nbsp;</i><code>])</code>
 * </div>
 * <p>
 * The purpose of the second construct is to provide a
 * generic way of adding an object type identifier in the
 * form of a URL or other text data to CBOR objects.
 * The CBOR tag <b>must</b> in this case be <code>{@value #RESERVED_TAG_COTX}</code>. 
 * Example:
 * </p>
 * <div style='margin-left:4em'><code>
 * {@value #RESERVED_TAG_COTX}(["https://example.com/myobject", {<br>
 * &nbsp;&nbsp;"amount": "145.00",<br>
 * &nbsp;&nbsp;"currency": "USD"<br>
 * }])</code>
 * </div>
 * <p>
 * Note that the <code>big&nbsp;integer</code> type is dealt with
 * as a specific primitive, in spite of being a tagged object.
 * </p>
 */
public class CBORTag extends CBORObject {

    /**
     * CBOR representation.
     */
    long tagNumber;
    CBORObject object;
    
    /**
     * Current COTX tag: {@value #RESERVED_TAG_COTX}
     */
    public static final int RESERVED_TAG_COTX  = 1010;

    /**
     * Creates a COTX-tagged object.
     * 
     * @param typeUrl Type URL (or other string)
     * @param object Object
     */
    public CBORTag(String typeUrl, CBORObject object) {
        this(RESERVED_TAG_COTX, new CBORArray()
                                    .addObject(new CBORTextString(typeUrl))
                                    .addObject(object));
    }

    /**
     * Creates a CBOR tagged object.
     * 
     * @param tagNumber Tag number
     * @param object Object
     */
    public CBORTag(long tagNumber, CBORObject object) {
        this.tagNumber = tagNumber;
        this.object = object;
        nullCheck(object);
    }

    /**
     * Returns tagged object.
     * @return CBOR object
     */
    public CBORObject getObject() {
        return object;
    }

    /**
     * Returns tag number.
     * @return Tag number
     */
    public long getTagNumber() {
        return tagNumber;
    }

    @Override
    public CBORTypes getType() {
        return CBORTypes.TAG;
    }
    
    @Override
    public byte[] encode() {
        return ArrayUtil.add(encodeTagAndN(MT_TAG, tagNumber), object.encode());

    }
    
    @Override
    void internalToString(CBORObject.DiagnosticNotation cborPrinter) {
         cborPrinter.append(Long.toUnsignedString(tagNumber)).append('(');
         object.internalToString(cborPrinter);
         cborPrinter.append(')');
    }
}
