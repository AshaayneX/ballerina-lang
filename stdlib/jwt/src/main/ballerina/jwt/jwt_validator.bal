// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/cache;
import ballerina/crypto;
import ballerina/encoding;
import ballerina/io;
import ballerina/time;

# Represents JWT validator configurations.
# + issuer - Expected issuer
# + audience - Expected audience
# + clockSkew - Clock skew in seconds
# + trustStore - Trust store used for signature verification
# + certificateAlias - Token signed public key certificate alias
# + validateCertificate - Validate public key certificate notBefore and notAfter periods
# + jwtCache - Cache used to store parsed JWT information as CachedJwt
public type JwtValidatorConfig record {|
    string issuer?;
    string|string[] audience?;
    int clockSkew = 0;
    crypto:TrustStore trustStore?;
    string certificateAlias?;
    boolean validateCertificate?;
    cache:Cache jwtCache = new(capacity = 1000);
|};

# Represents parsed and cached JWT.
#
# + jwtPayload - Parsed JWT payload
# + expiryTime - Expiry time of the JWT
public type CachedJwt record {|
    JwtPayload jwtPayload;
    int expiryTime;
|};

# Validity given JWT string.
#
# + jwtToken - JWT token that need to validate
# + config - JWT validator config record
# + return - If JWT token is valied return the JWT payload. An `JwtError` if token validation fails.
public function validateJwt(string jwtToken, JwtValidatorConfig config) returns @tainted (JwtPayload|JwtError) {
    string[] encodedJWTComponents = [];
    var jwtComponents = getJWTComponents(jwtToken);
    if (jwtComponents is string[]) {
        encodedJWTComponents = jwtComponents;
    } else {
        return jwtComponents;
    }

    string[] aud = [];
    JwtHeader header = {};
    JwtPayload payload = {};
    var decodedJwt = parseJWT(encodedJWTComponents);
    if (decodedJwt is [JwtHeader, JwtPayload]) {
        [header, payload] = decodedJwt;
    } else {
        return decodedJwt;
    }

    var jwtValidity = validateJwtRecords(encodedJWTComponents, header, payload, config);
    if (jwtValidity is JwtError) {
        return jwtValidity;
    } else {
        if (jwtValidity) {
            return payload;
        } else {
            return prepareJwtError("Invalid JWT token.");
        }
    }
}

function getJWTComponents(string jwtToken) returns string[]|JwtError {
    string[] jwtComponents = jwtToken.split("\\.");
    if (jwtComponents.length() < 2 || jwtComponents.length() > 3) {
        return prepareJwtError("Invalid JWT token.");
    }
    return jwtComponents;
}

function parseJWT(string[] encodedJWTComponents) returns @tainted ([JwtHeader, JwtPayload]|JwtError) {
    json headerJson = {};
    json payloadJson = {};
    var decodedJWTComponents = getDecodedJWTComponents(encodedJWTComponents);
    if (decodedJWTComponents is [json, json]) {
        [headerJson, payloadJson] = decodedJWTComponents;
    } else {
        return decodedJWTComponents;
    }

    JwtHeader jwtHeader = parseHeader(headerJson);
    JwtPayload jwtPayload = check parsePayload(payloadJson);
    return [jwtHeader, jwtPayload];
}

function getDecodedJWTComponents(string[] encodedJWTComponents) returns @tainted ([json, json]|JwtError) {
    string jwtHeader = "";
    string jwtPayload = "";

    var decodeResult = encoding:decodeBase64Url(encodedJWTComponents[0]);
    if (decodeResult is byte[]) {
        jwtHeader = encoding:byteArrayToString(decodeResult);
    } else {
        return prepareJwtError("Base64 url decode failed for JWT header.", err = decodeResult);
    }

    decodeResult = encoding:decodeBase64Url(encodedJWTComponents[1]);
    if (decodeResult is byte[]) {
        jwtPayload = encoding:byteArrayToString(decodeResult);
    } else {
        return prepareJwtError("Base64 url decode failed for JWT payload.", err = decodeResult);
    }

    json jwtHeaderJson = {};
    json jwtPayloadJson = {};

    io:StringReader reader = new(jwtHeader);
    var jsonHeader = reader.readJson();
    if (jsonHeader is json) {
        jwtHeaderJson = jsonHeader;
    } else {
        return prepareJwtError("String to JSON conversion failed for JWT header.", err = jsonHeader);
    }

    reader = new(jwtPayload);
    var jsonPayload = reader.readJson();
    if (jsonPayload is json) {
        jwtPayloadJson = jsonPayload;
    } else {
        return prepareJwtError("String to JSON conversion failed for JWT paylaod.", err = jsonPayload);
    }
    return [jwtHeaderJson, jwtPayloadJson];
}

function parseHeader(json jwtHeaderJson) returns JwtHeader {
    JwtHeader jwtHeader = {};
    string[] keys = jwtHeaderJson.getKeys();
    foreach var key in keys {
        if (key == ALG) {
            if (jwtHeaderJson[key].toString() == "RS256") {
                jwtHeader.alg = RS256;
            } else if (jwtHeaderJson[key].toString() == "RS384") {
                jwtHeader.alg = RS384;
            } else if (jwtHeaderJson[key].toString() == "RS512") {
                jwtHeader.alg = RS512;
            }
        } else if (key == TYP) {
            jwtHeader.typ = jwtHeaderJson[key].toString();
        } else if (key == CTY) {
            jwtHeader.cty = jwtHeaderJson[key].toString();
        } else if (key == KID) {
            jwtHeader.kid = jwtHeaderJson[key].toString();
        }
    }
    return jwtHeader;
}

function parsePayload(json jwtPayloadJson) returns JwtPayload|JwtError {
    string[] aud = [];
    JwtPayload jwtPayload = {};
    map<json> customClaims = {};
    string[] keys = jwtPayloadJson.getKeys();
    foreach var key in keys {
        if (key == ISS) {
            jwtPayload.iss = jwtPayloadJson[key].toString();
        } else if (key == SUB) {
            jwtPayload.sub = jwtPayloadJson[key].toString();
        } else if (key == AUD) {
            jwtPayload.aud = check convertToStringArray(jwtPayloadJson[key]);
        } else if (key == JTI) {
            jwtPayload.jti = jwtPayloadJson[key].toString();
        } else if (key == EXP) {
            string exp = jwtPayloadJson[key].toString();
            var value = int.convert(exp);
            if (value is int) {
                jwtPayload.exp = value;
            } else {
                jwtPayload.exp = 0;
            }
        } else if (key == NBF) {
            string nbf = jwtPayloadJson[key].toString();
            var value = int.convert(nbf);
            if (value is int) {
                jwtPayload.nbf = value;
            } else {
                jwtPayload.nbf = 0;
            }
        } else if (key == IAT) {
            string iat = jwtPayloadJson[key].toString();
            var value = int.convert(iat);
            if (value is int) {
                jwtPayload.iat = value;
            } else {
                jwtPayload.iat = 0;
            }
        } else {
            customClaims[key] = jwtPayloadJson[key];
        }
    }
    jwtPayload.customClaims = customClaims;
    return jwtPayload;
}

function validateJwtRecords(string[] encodedJWTComponents, JwtHeader jwtHeader, JwtPayload jwtPayload,
                            JwtValidatorConfig config) returns boolean|JwtError {
    if (!validateMandatoryJwtHeaderFields(jwtHeader)) {
        return prepareJwtError("Mandatory field signing algorithm(alg) is empty in the given JWT.");
    }
    if (config["validateCertificate"] is ()) {
        config.validateCertificate = true;
    }
    if (config.validateCertificate == true && !check validateCertificate(config)) {
        return prepareJwtError("Public key certificate validity period has passed.");
    }
    var trustStore = config["trustStore"];
    if (trustStore is crypto:TrustStore) {
        var signatureValidationResult = validateSignature(encodedJWTComponents, jwtHeader, config);
        if (signatureValidationResult is JwtError) {
            return signatureValidationResult;
        }
    }
    var iss = config["issuer"];
    if (iss is string) {
        var issuerStatus = validateIssuer(jwtPayload, config);
        if (issuerStatus is JwtError) {
            return issuerStatus;
        }
    }
    var aud = config["audience"];
    if (aud is string || aud is string[]) {
        var audienceStatus = validateAudience(jwtPayload, config);
        if (audienceStatus is JwtError) {
            return audienceStatus;
        }
    }
    var exp = jwtPayload["exp"];
    if (exp is int) {
        if (!validateExpirationTime(jwtPayload, config)) {
            return prepareJwtError("JWT token is expired.");
        }
    }
    var nbf = jwtPayload["nbf"];
    if (nbf is int) {
        if (!validateNotBeforeTime(jwtPayload)) {
            return prepareJwtError("JWT token is used before Not_Before_Time.");
        }
    }
    //TODO : Need to validate jwt id (jti) and custom claims.
    return true;
}

function validateMandatoryJwtHeaderFields(JwtHeader jwtHeader) returns boolean {
    if (jwtHeader.alg == "") {
        return false;
    }
    return true;
}

function validateCertificate(JwtValidatorConfig config) returns boolean|JwtError {
    var publicKey = crypto:decodePublicKey(keyStore = config.trustStore, keyAlias = config.certificateAlias);
    if (publicKey is crypto:PublicKey) {
        time:Time currTimeInGmt = check time:toTimeZone(time:currentTime(), "GMT");
        int currTimeInGmtMillis = currTimeInGmt.time;

        var certificate = publicKey.certificate;
        if (certificate is crypto:Certificate) {
            int notBefore = certificate.notBefore.time;
            int notAfter = certificate.notAfter.time;
            if (currTimeInGmtMillis >= notBefore && currTimeInGmtMillis <= notAfter) {
                return true;
            }
        }
        return false;
    } else {
        return prepareJwtError("Public key decode failed.", err = publicKey);
    }
}

function validateSignature(string[] encodedJWTComponents, JwtHeader jwtHeader, JwtValidatorConfig config)
                           returns boolean|JwtError {
    if (jwtHeader.alg == NONE) {
        return prepareJwtError("Not a valid JWS. Signature algorithm is NONE.");
    } else {
        if (encodedJWTComponents.length() == 2) {
            return prepareJwtError("Not a valid JWS. Signature is required.");
        } else {
            string assertion = encodedJWTComponents[0] + "." + encodedJWTComponents[1];
            var signPart = encoding:decodeBase64Url(encodedJWTComponents[2]);
            if (signPart is byte[]) {
                var publicKey = crypto:decodePublicKey(keyStore = config.trustStore, keyAlias = config.certificateAlias);
                if (publicKey is crypto:PublicKey) {
                    if (jwtHeader.alg == RS256) {
                        var verification = crypto:verifyRsaSha256Signature(assertion.toByteArray("UTF-8"), signPart, publicKey);
                        if (verification is boolean) {
                            return verification;
                        } else {
                            return prepareJwtError("SHA256 singature verification failed.", err = verification);
                        }
                    } else if (jwtHeader.alg == RS384) {
                        var verification = crypto:verifyRsaSha384Signature(assertion.toByteArray("UTF-8"), signPart, publicKey);
                        if (verification is boolean) {
                            return verification;
                        } else {
                            return prepareJwtError("SHA384 singature verification failed.", err = verification);
                        }
                    } else if (jwtHeader.alg == RS512) {
                        var verification = crypto:verifyRsaSha512Signature(assertion.toByteArray("UTF-8"), signPart, publicKey);
                        if (verification is boolean) {
                            return verification;
                        } else {
                            return prepareJwtError("SHA512 singature verification failed.", err = verification);
                        }
                    } else {
                        return prepareJwtError("Unsupported JWS algorithm.");
                    }
                } else {
                    return prepareJwtError("Public key decode failed.", err = publicKey);
                }
            } else {
                return prepareJwtError("Base64 url decode failed for JWT signature.", err = signPart);
            }
        }
    }
}

function validateIssuer(JwtPayload jwtPayload, JwtValidatorConfig config) returns JwtError? {
    var iss = jwtPayload["iss"];
    if (iss is string) {
        if (jwtPayload.iss != config.issuer) {
            return prepareJwtError("JWT contained invalid issuer name : " + jwtPayload.iss);
        }
    } else {
        return prepareJwtError("JWT must contain a valid issuer name.");
    }
}

function validateAudience(JwtPayload jwtPayload, JwtValidatorConfig config) returns JwtError? {
    var audiencePayload = jwtPayload["aud"];
    var audienceConfig = config.audience;
    if (audiencePayload is string) {
        if (audienceConfig is string) {
            if (audiencePayload == audienceConfig) {
                return ();
            }
        } else {
            foreach string audience in audienceConfig {
                if (audience == audiencePayload) {
                    return ();
                }
            }
        }
        return prepareJwtError("Invalid audience.");
    } else if (audiencePayload is string[]) {
        if (audienceConfig is string) {
            foreach string audience in audiencePayload {
                if (audience == audienceConfig) {
                    return ();
                }
            }
        } else {
            foreach string audienceC in audienceConfig {
                foreach string audienceP in audiencePayload {
                    if (audienceC == audienceP) {
                        return ();
                    }
                }
            }
        }
        return prepareJwtError("Invalid audience.");
    } else {
        return prepareJwtError("JWT must contain a valid audience.");
    }
}

function validateExpirationTime(JwtPayload jwtPayload, JwtValidatorConfig config) returns boolean {
    //Convert current time which is in milliseconds to seconds.
    int expTime = jwtPayload.exp;
    if (config.clockSkew > 0){
        expTime = expTime + config.clockSkew;
    }
    return expTime > time:currentTime().time / 1000;
}

function validateNotBeforeTime(JwtPayload jwtPayload) returns boolean {
    return time:currentTime().time > (jwtPayload["nbf"] ?: 0);
}

function convertToStringArray(json jsonData) returns string[]|JwtError {
    if (jsonData is json[]) {
        var result = string[].convert(jsonData);
        if (result is string[]) {
            return result;
        } else {
            return prepareJwtError("JSON-Data to String convertion failed.", err = result);
        }
    } else {
        return [jsonData.toString()];
    }
}