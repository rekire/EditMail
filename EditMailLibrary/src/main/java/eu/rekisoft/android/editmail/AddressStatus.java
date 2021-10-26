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
    pending;

    private String mail;

    /**
     * @return the suggested mail address.
     */
    public String getMailAddress() {
        return mail;
    }

    /**
     * Set the email address in case of a detected typo.
     *
     * @param mail The mail address which is guessed.
     * @return this.
     */
    protected AddressStatus setMailAddress(String mail) {
        this.mail = mail;
        return this;
    }

    /**
     * @return <code>true</code> if the email address is not valid or a typo was detected.
     */
    public boolean wrong() {
        return this != valid && this != typoDetected;
    }
}
