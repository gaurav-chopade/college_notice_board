package com.college.noticeboard.entity;

/**
 * Lifecycle of an account. Only APPROVED accounts can sign in or use a token.
 * <ul>
 *     <li>PENDING: self-registered, waiting for the responsible approver.</li>
 *     <li>APPROVED: can use the system.</li>
 *     <li>REJECTED: application turned down (with a reason); the person may apply again.</li>
 *     <li>SUSPENDED: temporarily blocked by the administrator (with a reason).</li>
 *     <li>DEACTIVATED: closed account, kept so notices and audit history stay intact.</li>
 * </ul>
 */
public enum AccountStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED,
    DEACTIVATED
}
