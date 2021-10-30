package eu.rekisoft.android.editmail;

/**
 * The status of the mail address verification.
 *
 * @author René Kilczan
 */
public enum AddressStatus {
    /**
     * The address seems to be fine
     */
    valid,
    /**
     * The address has some schema errors like a missing at sign
     */
    wrongSchema,
    /**
     * The domain is currently not registered
     */
    notRegistered,
    /**
     * The domain has no MX record (so the domain cannot receive mails)
     */
    noMxRecord,
    /**
     * A typographic error was detected
     */
    typoDetected,
    /**
     * The status of the address is unknown
     */
    unknown,
    /**
     * The mail address check is pending
     */
    pending
}
