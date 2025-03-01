/*
 * Copyright (C) 2015 jsonwebtoken.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jsonwebtoken.impl.crypto

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.SignatureException
import org.junit.Test

import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

import static org.junit.Assert.*

class EllipticCurveSignatureValidatorTest {

    @Test
    void testDoVerifyWithInvalidKeyException() {

        String msg = 'foo'
        final InvalidKeyException ex = new InvalidKeyException(msg)

        def v = new EllipticCurveSignatureValidator(SignatureAlgorithm.ES512, EllipticCurveProvider.generateKeyPair().public) {
            @Override
            protected boolean doVerify(Signature sig, PublicKey pk, byte[] data, byte[] signature) throws InvalidKeyException, java.security.SignatureException {
                throw ex;
            }
        }

        byte[] bytes = new byte[16]
        byte[] signature = new byte[16]
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(bytes)
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(signature)

        try {
            v.isValid(bytes, signature)
            fail();
        } catch (SignatureException se) {
            assertEquals se.message, 'Unable to verify Elliptic Curve signature using configured ECPublicKey. ' + msg
            assertSame se.cause, ex
        }
    }

    @Test
    void ecdsaSignatureComplianceTest() {
        def fact = KeyFactory.getInstance("EC");
        def publicKey = "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQASisgweVL1tAtIvfmpoqvdXF8sPKTV9YTKNxBwkdkm+/auh4pR8TbaIfsEzcsGUVv61DFNFXb0ozJfurQ59G2XcgAn3vROlSSnpbIvuhKrzL5jwWDTaYa5tVF1Zjwia/5HUhKBkcPuWGXg05nMjWhZfCuEetzMLoGcHmtvabugFrqsAg="
        def pub = fact.generatePublic(new X509EncodedKeySpec(Decoders.BASE64.decode(publicKey)))
        def v = new EllipticCurveSignatureValidator(SignatureAlgorithm.ES512, pub)
        def verifier = { token ->
            def signatureStart = token.lastIndexOf('.')
            def withoutSignature = token.substring(0, signatureStart)
            def signature = token.substring(signatureStart + 1)
            assert v.isValid(withoutSignature.getBytes("US-ASCII"), Decoders.BASE64URL.decode(signature)), "Signature do not match that of other implementations"
        }
        //Test verification for token created using https://github.com/auth0/node-jsonwebtoken/tree/v7.0.1
        verifier("eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30.Aab4x7HNRzetjgZ88AMGdYV2Ml7kzFbl8Ql2zXvBores7iRqm2nK6810ANpVo5okhHa82MQf2Q_Zn4tFyLDR9z4GAcKFdcAtopxq1h8X58qBWgNOc0Bn40SsgUc8wOX4rFohUCzEtnUREePsvc9EfXjjAH78WD2nq4tn-N94vf14SncQ")
        //Test verification for token created using https://github.com/jwt/ruby-jwt/tree/v1.5.4
        verifier("eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzUxMiJ9.eyJ0ZXN0IjoidGVzdCJ9.AV26tERbSEwcoDGshneZmhokg-tAKUk0uQBoHBohveEd51D5f6EIs6cskkgwtfzs4qAGfx2rYxqQXr7LTXCNquKiAJNkTIKVddbPfped3_TQtmHZTmMNiqmWjiFj7Y9eTPMMRRu26w4gD1a8EQcBF-7UGgeH4L_1CwHJWAXGbtu7uMUn")
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void legacySignatureCompatDefaultTest() {
        def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
        def keypair = EllipticCurveProvider.generateKeyPair()
        def signature = Signature.getInstance(SignatureAlgorithm.ES512.jcaName)
        def data = withoutSignature.getBytes("US-ASCII")
        signature.initSign(keypair.private)
        signature.update(data)
        def signed = signature.sign()
        assertFalse new EllipticCurveSignatureValidator(SignatureAlgorithm.ES512, keypair.public).isValid(data, signed)
    }

    @Test
    void legacySignatureCompatWhenEnabledTest() {
        try {
            System.setProperty(EllipticCurveSignatureValidator.DER_ENCODING_SYS_PROPERTY_NAME, 'true')
            def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
            def keypair = EllipticCurveProvider.generateKeyPair()
            def signature = Signature.getInstance(SignatureAlgorithm.ES512.jcaName)
            def data = withoutSignature.getBytes("US-ASCII")
            signature.initSign(keypair.private)
            signature.update(data)
            def signed = signature.sign()
            assertTrue new EllipticCurveSignatureValidator(SignatureAlgorithm.ES512, keypair.public).isValid(data, signed)
        } finally {
            System.clearProperty(EllipticCurveSignatureValidator.DER_ENCODING_SYS_PROPERTY_NAME)
        }
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void testSignatureAllZeros() {
        try {
            byte[] forgedSig = new byte[64]
            def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
            def keypair = EllipticCurveProvider.generateKeyPair()
            def data = withoutSignature.getBytes("US-ASCII")
            new EllipticCurveSignatureValidator(SignatureAlgorithm.ES256, keypair.public).isValid(data, forgedSig)
            fail("SignatureException expected")
        } catch(SignatureException expected) {
            assertEquals 'Unable to verify Elliptic Curve signature using configured ECPublicKey. Invalid ECDSA signature format.', expected.getMessage()
        }
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void testSignatureRZero() {
        try {
            byte[] r = new byte[32]
            byte[] s = new byte[32]; Arrays.fill(s, Byte.MAX_VALUE)
            byte[] sig = new byte[r.length + s.length]
            System.arraycopy(r, 0, sig, 0, r.length)
            System.arraycopy(s, 0, sig, r.length, s.length)

            def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
            def keypair = EllipticCurveProvider.generateKeyPair()
            def data = withoutSignature.getBytes("US-ASCII")
            new EllipticCurveSignatureValidator(SignatureAlgorithm.ES256, keypair.public).isValid(data, sig)
            fail("SignatureException expected")
        } catch(SignatureException expected) {
            assertEquals 'Unable to verify Elliptic Curve signature using configured ECPublicKey. Invalid ECDSA signature format.', expected.getMessage()
        }
    }

    @Test // asserts guard for JVM security bug CVE-2022-21449:
    void testSignatureSZero() {
        try {
            byte[] r = new byte[32]; Arrays.fill(r, Byte.MAX_VALUE);
            byte[] s = new byte[32]
            byte[] sig = new byte[r.length + s.length]
            System.arraycopy(r, 0, sig, 0, r.length)
            System.arraycopy(s, 0, sig, r.length, s.length)

            def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
            def keypair = EllipticCurveProvider.generateKeyPair()
            def data = withoutSignature.getBytes("US-ASCII")
            new EllipticCurveSignatureValidator(SignatureAlgorithm.ES256, keypair.public).isValid(data, sig)
            fail("SignatureException expected")
        } catch(SignatureException expected) {
            assertEquals 'Unable to verify Elliptic Curve signature using configured ECPublicKey. Invalid ECDSA signature format.', expected.getMessage()
        }
    }

    @Test
    void invalidAlgorithmTest() {
        def invalidAlgorithm = SignatureAlgorithm.HS256
        try {
            EllipticCurveProvider.getSignatureByteArrayLength(invalidAlgorithm)
            fail()
        } catch (JwtException e) {
            assertEquals e.message, 'Unsupported Algorithm: ' + invalidAlgorithm.name()
        }
    }

    @Test
    void invalidECDSASignatureFormatTest() {
        try {
            def signature = new byte[257]
            SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(signature)
            EllipticCurveProvider.transcodeSignatureToDER(signature)
            fail()
        } catch (JwtException e) {
            assertEquals e.message, 'Invalid ECDSA signature format.'
        }
    }

    @Test
    void invalidDERSignatureToJoseFormatTest() {
        def verify = { signature ->
            try {
                EllipticCurveProvider.transcodeSignatureToConcat(signature, 132)
                fail()
            } catch (JwtException e) {
                assertEquals e.message, 'Invalid ECDSA signature format'
            }
        }
        def signature = new byte[257]
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(signature)
        //invalid type
        signature[0] = 34
        verify(signature)
        def shortSignature = new byte[7]
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(shortSignature)
        verify(shortSignature)
        signature[0] = 48
//        signature[1] = 0x81
        signature[1] = -10
        verify(signature)
    }

    @Test
    void testPaddedSignatureToDER() {
        def signature = new byte[32]
        SignatureProvider.DEFAULT_SECURE_RANDOM.nextBytes(signature)
        signature[0] = 0 as byte
        EllipticCurveProvider.transcodeSignatureToDER(signature) //no exception
    }

    @Test
    void edgeCaseSignatureToConcatLengthTest() {
        try {
            def signature = Decoders.BASE64.decode("MIEAAGg3OVb/ZeX12cYrhK3c07TsMKo7Kc6SiqW++4CAZWCX72DkZPGTdCv2duqlupsnZL53hiG3rfdOLj8drndCU+KHGrn5EotCATdMSLCXJSMMJoHMM/ZPG+QOHHPlOWnAvpC1v4lJb32WxMFNz1VAIWrl9Aa6RPG1GcjCTScKjvEE")
            EllipticCurveProvider.transcodeSignatureToConcat(signature, 132)
            fail()
        } catch (JwtException e) {

        }
    }

    @Test
    void edgeCaseSignatureToConcatInvalidSignatureTest() {
        try {
            def signature = Decoders.BASE64.decode("MIGBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            EllipticCurveProvider.transcodeSignatureToConcat(signature, 132)
            fail()
        } catch (JwtException e) {
            assertEquals e.message, 'Invalid ECDSA signature format'
        }
    }

    @Test
    void edgeCaseSignatureToConcatInvalidSignatureBranchTest() {
        try {
            def signature = Decoders.BASE64.decode("MIGBAD4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            EllipticCurveProvider.transcodeSignatureToConcat(signature, 132)
            fail()
        } catch (JwtException e) {
            assertEquals e.message, 'Invalid ECDSA signature format'
        }
    }

    @Test
    void edgeCaseSignatureToConcatInvalidSignatureBranch2Test() {
        try {
            def signature = Decoders.BASE64.decode("MIGBAj4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            EllipticCurveProvider.transcodeSignatureToConcat(signature, 132)
            fail()
        } catch (JwtException e) {
            assertEquals e.message, 'Invalid ECDSA signature format'
        }
    }

    @Test
    void verifySwarmTest() {
        for (SignatureAlgorithm algorithm : [SignatureAlgorithm.ES256, SignatureAlgorithm.ES384, SignatureAlgorithm.ES512]) {
            def withoutSignature = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJ0ZXN0IjoidGVzdCIsImlhdCI6MTQ2NzA2NTgyN30"
            def keypair = EllipticCurveProvider.generateKeyPair()
            def data = withoutSignature.getBytes("US-ASCII")
            def signature = new EllipticCurveSigner(algorithm, keypair.private).sign(data)
            assert new EllipticCurveSignatureValidator(algorithm, keypair.public).isValid(data, signature)
        }
    }
}
