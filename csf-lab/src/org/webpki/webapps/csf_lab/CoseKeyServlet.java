/*
 *  Copyright 2006-2019 WebPKI.org (http://webpki.org).
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
package org.webpki.webapps.csf_lab;

import java.io.IOException;

import java.security.KeyPair;
import java.security.PublicKey;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.webpki.cbor.CBORInteger;
import org.webpki.cbor.CBORKeyPair;
import org.webpki.cbor.CBORMap;
import org.webpki.cbor.CBORObject;
import org.webpki.cbor.CBORPublicKey;

import org.webpki.crypto.AlgorithmPreferences;

import org.webpki.json.JSONCryptoHelper;
import org.webpki.json.JSONObjectReader;
import org.webpki.json.JSONObjectWriter;
import org.webpki.json.JSONParser;

import org.webpki.util.PEMDecoder;

import org.webpki.webutil.ServletUtil;

public class CoseKeyServlet extends CoreRequestServlet {

    private static final long serialVersionUID = 1L;
    
    static final String KEY_IN      = "keyin";
    
    void setRSAParameter(JSONObjectReader jwk, String jsonArgument, CBORMap cbor, int cborLabel)
            throws IOException {
        cbor.setByteString(new CBORInteger(cborLabel), jwk.getBinary(jsonArgument));
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        JSONObjectWriter jsonResponse = new JSONObjectWriter();
        try {
            request.setCharacterEncoding("utf-8");
            if (!request.getContentType().equals("application/json")) {
                throw new IOException("Unexpected MIME type:" + request.getContentType());
            }
            JSONObjectReader parsedJson = JSONParser.parse(ServletUtil.getData(request));
            String inData = parsedJson.getString(KEY_IN).trim();
            KeyPair keyPair = null;
            PublicKey publicKey = null;
             // is it a JWK?
            if (inData.startsWith("{")) {
                JSONObjectReader jwk = JSONParser.parse(inData);
                try {
                    publicKey = JSONCryptoHelper.decodePublicKey(jwk, AlgorithmPreferences.JOSE);
                    if (jwk.hasProperty("d")) {
                        // Success! However this is actually a private key.
                        keyPair = new KeyPair(publicKey, 
                                              JSONCryptoHelper.decodePrivateKey(jwk, publicKey));
                    }
                    jwk.checkForUnread();
                } catch (Exception e) {
                    throw new IOException("Undecodable JWK: " + e.getMessage());
                }
            } else {
                byte[] keyInBinary = inData.getBytes("utf-8");
                try {
                    keyPair = PEMDecoder.getKeyPair(keyInBinary);
                } catch (Exception e) {
                    try {
                        publicKey = PEMDecoder.getPublicKey(keyInBinary);
                    } catch (Exception e1) {
                        throw new IOException("Undecodable PEM");
                    }
                }
            }
            
            // Now we have either just a public key or a key pair
            CBORMap cbor = keyPair == null ? 
                    CBORPublicKey.encode(publicKey) : CBORKeyPair.encode(keyPair);
            jsonResponse.setString(CBOR_OUT, 
                                   getFormattedCbor(parsedJson, new CBORObject[] {cbor}));
        } catch (Exception e) {
            jsonResponse.setString(ERROR, HTML.encode(e.getMessage()).replace("\n", "<br>")
                                                                     .replace(" ","&nbsp;"));
        }
        returnJSON(response, jsonResponse);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        StringBuilder js = new StringBuilder(
                "'use strict';\n" +
                "function getRadioValue(name) {\n" +
                "  let ele = document.getElementsByName(name);\n" +
                "  for (var i = 0; i < ele.length; i++) {\n" +
                "    if (ele[i].checked) {\n" +
                "      return ele[i].value;\n" +
                "    }\n" +
                "  }\n" +
                "}\n" +

                "async function delayedConvert() {\n" +
                "  document.getElementById('" + CBOR_OUT + "').children[1].innerHTML = 'Working...';\n" +
                "  setTimeout(function() {\n" +
                "    convertToCose();\n" +
                "  }, 200);\n" +
                "}\n" +
                "async function convertToCose() {\n" +
                "  let jsonObject = {" +
                   KEY_IN + ": document.getElementById('" + KEY_IN + "').children[1].value," +
                   SEL_OUT + ": getRadioValue('" + SEL_OUT + "')" +
                   "};\n" +
                "  let html = 'unknown error';\n" +
                "  try {\n" +
                "    const response = await fetch('cosekey', {\n" +
                "      headers: {\n" +
                "        'Content-Type': '" + JSON_CONTENT_TYPE + "'\n" +
                "      },\n" +
                "      method: 'POST',\n" +
                "      body: JSON.stringify(jsonObject)\n" +
                "    });\n" +
                "    if (response.ok) {\n" +
                "      const jsonResult = await response.json();\n" +
                "      html = jsonResult." + ERROR + 
                    "? '<span style=\"color:red;font-weight:bold\">' + " +
                    "jsonResult." + ERROR + " + '</span>' : jsonResult." + CBOR_OUT + ";\n" +
                "    }\n" +
                "  } catch (e) {\n" +
                "    html = '<span style=\"color:red;font-weight:bold\">' + e + '</span>';\n" +
                "  }\n" +
                "  document.getElementById('" + CBOR_OUT + "').children[1].innerHTML = html;\n" +
                "}\n");

        StringBuilder html = new StringBuilder(
                "<div class='header'>Key Conversion Utility</div>" +
                "<div style='padding-top:15pt'>" + 
                "This utility converts public and private keys supplied in JWK or PEM format to " +
                "their COSE counterpart.</div>")
            .append(HTML.fancyText(
                        true,
                        KEY_IN,
                        10, 
                        "{\n" +
                        "  \"kty\": \"OKP\",\n" +
                        "  \"crv\": \"Ed25519\",\n" +
                        "  \"x\": \"_kms9bkrbpI1lPLoM2j2gKySS-k89TOuyvgC43dX-Mk\",\n" +
                        "  \"d\": \"0flr-6bXs459f9qwAq20Zs3NizTGIEH5_rTDFoumFV4\"\n" +
                        "}",
                        "Paste a key object in the text box or try with the default"))
            .append(HTML.fancyBox(
                        CBOR_OUT,
                        "",
                        selector(SEL_OUT, false) + "Converted result"))           
            .append(
                "<div style='display:flex;justify-content:center'>" +
                "<div class='stdbtn' onclick=\"delayedConvert()\">" +
                "Convert!" +
                "</div>" +
                "</div>" +
                "<div>&nbsp;</div>");
        HTML.standardPage(response, js.toString(), html);
    }
}
