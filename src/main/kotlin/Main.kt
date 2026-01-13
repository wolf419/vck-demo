package org.example

import at.asitplus.openid.OidcUserInfo
import at.asitplus.openid.OidcUserInfoExtended
import at.asitplus.wallet.lib.agent.ValidatorVcJws
import at.asitplus.wallet.eupid.EuPidCredential
import at.asitplus.wallet.eupid.EuPidScheme
import at.asitplus.wallet.eupid.Initializer
import at.asitplus.wallet.lib.agent.CredentialToBeIssued
import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.HolderAgent
import at.asitplus.wallet.lib.agent.Issuer
import at.asitplus.wallet.lib.agent.IssuerAgent
import at.asitplus.wallet.lib.agent.SubjectCredentialStore
import at.asitplus.wallet.lib.agent.Verifier
import at.asitplus.wallet.lib.agent.toStoreCredentialInput
import at.asitplus.wallet.lib.data.LocalDateOrInstant
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
suspend fun main() {
    Initializer.initWithVCK()

    // Create the key material
    val issuerKey = EphemeralKeyWithoutCert()
    val holderKey = EphemeralKeyWithoutCert()

    // Create the issuer agent
    val issuer = IssuerAgent(
        identifier = UniformResourceIdentifier("https://issuer.example"),
        keyMaterial = issuerKey
    )

    // Create the holder agent
    val holder = HolderAgent(keyMaterial = holderKey)

    // Create the credential based on the chosen scheme
    // Credential schemes are separate from vck
    val now = Clock.System.now()
    val pidSubject = EuPidCredential(
        id = holderKey.publicKey.didEncoded,
        familyName = "Doe",
        givenName = "Alice",
        birthDate = LocalDate(1990, 1, 1),
        issuanceDate = LocalDateOrInstant.Instant(now),
        expiryDate = LocalDateOrInstant.Instant(now + 365.days),
        issuingAuthority = "AT",
        issuingCountry = "AT",
    )

    // Create the credential to be issued
    val toBeIssued = CredentialToBeIssued.VcJwt(
        subject = pidSubject,
        expiration = now + 365.days,
        scheme = EuPidScheme,
        subjectPublicKey = holderKey.key.publicKey,
        userInfo = OidcUserInfoExtended(OidcUserInfo("Alice"))
    )

    // Issue the credential
    val issued = issuer.issueCredential(toBeIssued).getOrThrow()
    val vcJwtIssued = issued as Issuer.IssuedCredential.VcJwt

    // Store the credential
    holder.storeCredential(vcJwtIssued.toStoreCredentialInput())

    // Get the JWS from the credential store
    val storeEntry = holder.getCredentials()?.first() as SubjectCredentialStore.StoreEntry.Vc
    val jwsString = storeEntry.vcSerialized

    // Verify the credential
    // Public key refers to the holder key for holder binding
    val validator = ValidatorVcJws()
    when (val result = validator.verifyVcJws(jwsString, publicKey = holderKey.publicKey)) {
        is Verifier.VerifyCredentialResult.SuccessJwt -> {
            println("OK: ${result.jws.vc.credentialSubject}")
        }

        is Verifier.VerifyCredentialResult.ValidationError -> {
            println("Validation error: ${result.cause}")
        }

        else -> println("Not a VC-JWT")
    }
}