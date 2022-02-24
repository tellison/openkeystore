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
 * Class for holding CBOR floating point numbers.
 * <p>
 * Numbers are constrained to the IEEE 754 notation
 * using the length 16, 32, and 64 bit on "wire".  Which
 * length to use is governed by the size and precision 
 * required to (minimally) correctly represent a number.
 * API-wise numbers are only communicated as
 * 64-bit items (Java double).
 * </p>
 */
public class CBORFloatingPoint extends CBORObject {

    double value;
    
    /**
     * CBOR representation of value
     */
    int tag;
    long bitFormat;
    
    /**
     * Creates a CBOR <code>floating point</code>.
     * 
     * @param value
     */
    public CBORFloatingPoint(double value) {
        this.value = value;
        
        // Initial assumption: it is a plain vanilla 64-bit double.
        tag = MT_FLOAT64;
        bitFormat = Double.doubleToLongBits(value);

        // Check for possible edge cases.
        if ((bitFormat & ~FLOAT64_NEG_ZERO) == FLOAT64_POS_ZERO) {
            // Some zeroes are more zero than others :)
            tag = MT_FLOAT16;
            bitFormat = (bitFormat == FLOAT64_POS_ZERO) ? FLOAT16_POS_ZERO : FLOAT16_NEG_ZERO;
        } else if ((bitFormat & FLOAT64_POS_INFINITY) == FLOAT64_POS_INFINITY) {
            // Special "number".
            tag = MT_FLOAT16;
            bitFormat = (bitFormat == FLOAT64_POS_INFINITY) ?
                FLOAT16_POS_INFINITY : (bitFormat == FLOAT64_NEG_INFINITY) ?
                    // Deterministic representation of NaN => No NaN "signaling".
                    FLOAT16_NEG_INFINITY : FLOAT16_NOT_A_NUMBER;
        } else {
            // It is apparently a regular number.

            // Does the number fit in a 32-bit float?
            if (value != (double)((float) value)) {
                // Apparently it did not.  Note that the test above presumes that a conversion from
                // double to float returns Infinity or NaN for values that are out of range.
                // See sub-directory doc-files for another solution.
                return;
            }

            // New assumption: we settle on 32-bit float representation.
            tag = MT_FLOAT32;
            bitFormat = Float.floatToIntBits((float)value) & MASK_LOWER_32;
            
            // However, we must still check if the number could fit in a 16-bit float.
            long exponent = ((bitFormat >>> FLOAT32_FRACTION_SIZE) & 
                ((1l << FLOAT32_EXPONENT_SIZE) - 1)) -
                    (FLOAT32_EXPONENT_BIAS - FLOAT16_EXPONENT_BIAS);
            if (exponent > (FLOAT16_EXPONENT_BIAS << 1)) {
                // Too big for float16 or into the space reserved for NaN and Infinity.
                return;
            }

            long fraction = bitFormat & ((1l << FLOAT32_FRACTION_SIZE) - 1);
            if ((fraction & (1l << (FLOAT32_FRACTION_SIZE - FLOAT16_FRACTION_SIZE)) -1) != 0) {
                // Losing fraction bits is not an option.
                return;
            }
            fraction >>= (FLOAT32_FRACTION_SIZE - FLOAT16_FRACTION_SIZE);

            // Check if we need to denormalize data.
            if (exponent <= 0) {
                // The implicit "1" becomes explicit using subnormal representation.
                fraction += 1l << FLOAT16_FRACTION_SIZE;
                exponent--;
                // Always do at least one turn.
                do {
                    if ((fraction & 1) != 0) {
                        // Too off scale for float16.
                        // This test also catches subnormal float32 numbers.
                        return;
                    }
                    fraction >>= 1;
                } while (++exponent < 0);
            }

            // Seems like 16 bits indeed are sufficient!
            tag = MT_FLOAT16;
            bitFormat = 
                // Put possible sign bit in position.
                ((bitFormat >>> (32 - 16)) & FLOAT16_NEG_ZERO) +
                // Exponent.  Put it in front of fraction.
                (exponent << FLOAT16_FRACTION_SIZE) +
                // Fraction.
                fraction;
        }
    }

    /**
     * A slightly nicer formatter than Java's original
     * 
     * @param value The double
     * @return The double in string format
     */
    public static String formatDouble(double value) {
        return Double.toString(value).replace('E', 'e').replaceAll("e(\\d)", "e+$1");
    }

    @Override
    CBORTypes internalGetType() {
        return CBORTypes.FLOATING_POINT;
    }
    
    @Override
    byte[] internalEncode() throws IOException {
        return encodeTagAndValue(tag, 2 << (tag - MT_FLOAT16), bitFormat);
    }
    
    @Override
    void internalToString(CBORObject.DiagnosticNotation cborPrinter) {
         cborPrinter.append(formatDouble(value));
    }
}
