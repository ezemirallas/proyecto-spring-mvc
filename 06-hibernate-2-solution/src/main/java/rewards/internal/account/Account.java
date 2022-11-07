package rewards.internal.account;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;


import rewards.AccountContribution;
import rewards.AccountContribution.Distribution;

import common.money.MonetaryAmount;
import common.money.Percentage;

/**
 * An account for a member of the reward network. An account has one or more beneficiaries whose allocations must add up
 * to 100%.
 * 
 * An account can make contributions to its beneficiaries. Each contribution is distributed among the beneficiaries
 * based on an allocation.
 * 
 * An entity. An aggregate.
 */

@Entity
@Table(name="T_ACCOUNT")
public class Account {

	private Long entityId;
	
	private String number;

	private String name;

	private Set<Beneficiary> beneficiaries = new HashSet<Beneficiary>();

	private String creditCardNumber;

	@SuppressWarnings("unused")
	private Account() {
	}

	/**
	 * Create a new account.
	 * @param number the account number
	 * @param name the name on the account
	 */
	public Account(String number, String name) {
		this.number = number;
		this.name = name;
	}
	
	@Id 
    @GeneratedValue(strategy = GenerationType.AUTO)    
    @Column(name="ID", unique=true, nullable=false)	
	public Long getEntityId() {
		return entityId;
	}

	public void setEntityId(Long entityId) {
		this.entityId = entityId;
	}

	/**
	 * Getter for the credit card number for this account.
	 * 
	 * @return the credit card number for this account as a 16-character String.
	 */
	@Column(name="CREDIT_CARD", nullable=false, length=16)
	public String getCreditCardNumber() {
		return creditCardNumber;
	}
	
	/**
	 * Setter for the credit card number for this account.
	 * 
	 * @param creditCardNumber
	 */
	public void setCreditCardNumber(String creditCardNumber) {
		this.creditCardNumber = creditCardNumber;
	}

	/**
	 * Returns the number used to uniquely identify this account.
	 */
	@Column(name="NUMBER", nullable=false, length=9)
	public String getNumber() {
		return number;
	}
	
	public void setNumber(String number) {
		this.number = number;
	}

	/**
	 * Returns the name on file for this account.
	 */
	@Column(name="NAME", nullable=false, length=50)
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Add a single beneficiary with a 100% allocation percentage.
	 * @param beneficiaryName the name of the beneficiary (should be unique)
	 */
	public void addBeneficiary(String beneficiaryName) {
		addBeneficiary(beneficiaryName, Percentage.oneHundred());
	}

	/**
	 * Add a single beneficiary with the specified allocation percentage.
	 * @param beneficiaryName the name of the beneficiary (should be unique)
	 * @param allocationPercentage the beneficiary's allocation percentage within this account
	 */
	public void addBeneficiary(String beneficiaryName, Percentage allocationPercentage) {
		beneficiaries.add(new Beneficiary(beneficiaryName, allocationPercentage));
	}

	/**
	 * Validation check that returns true only if the total beneficiary allocation adds up to 100%.
	 */
	@Transient
	public boolean isValid() {
		Percentage totalPercentage = Percentage.zero();
		for (Beneficiary b : beneficiaries) {
			try {
				totalPercentage = totalPercentage.add(b.getAllocationPercentage());
			} catch (IllegalArgumentException e) {
				// total would have been over 100% - return invalid
				return false;
			}
		}
		if (totalPercentage.equals(Percentage.oneHundred())) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Make a monetary contribution to this account. The contribution amount is distributed among the account's
	 * beneficiaries based on each beneficiary's allocation percentage.
	 * @param amount the total amount to contribute
	 * @param contribution the contribution summary
	 */
	public AccountContribution makeContribution(MonetaryAmount amount) {
		if (!isValid()) {
			throw new IllegalStateException(
					"Cannot make contributions to this account: it has invalid beneficiary allocations");
		}
		Set<Distribution> distributions = distribute(amount);
		return new AccountContribution(getNumber(), amount, distributions);
	}

	/**
	 * Distribute the contribution amount among this account's beneficiaries.
	 * @param amount the total contribution amount
	 * @return the individual beneficiary distributions
	 */
	private Set<Distribution> distribute(MonetaryAmount amount) {
		Set<Distribution> distributions = new HashSet<Distribution>(beneficiaries.size());
		for (Beneficiary beneficiary : beneficiaries) {
			MonetaryAmount distributionAmount = amount.multiplyBy(beneficiary.getAllocationPercentage());
			beneficiary.credit(distributionAmount);
			Distribution distribution = new Distribution(beneficiary.getName(), distributionAmount, beneficiary
					.getAllocationPercentage(), beneficiary.getSavings());
			distributions.add(distribution);
		}
		return distributions;
	}

	/**
	 * Returns the beneficiaries for this account. Callers should not attempt to hold on or modify the returned set.
	 * This method should only be used transitively; for example, called to facilitate account reporting.
	 * @return the beneficiaries of this account
	 */
	@OneToMany(mappedBy = "account", fetch = FetchType.EAGER)
	public Set<Beneficiary> getBeneficiaries() {
		return beneficiaries;
	}

	public void setBeneficiaries(Set<Beneficiary> beneficiaries) {
		this.beneficiaries = beneficiaries;
	}

	/**
	 * Returns a single account beneficiary. Callers should not attempt to hold on or modify the returned object. This
	 * method should only be used transitively; for example, called to facilitate reporting or testing.
	 * @param name the name of the beneficiary e.g "Annabelle"
	 * @return the beneficiary object
	 */
	public Beneficiary getBeneficiary(String name) {
		for (Beneficiary b : beneficiaries) {
			if (b.getName().equals(name)) {
				return b;
			}
		}
		throw new IllegalArgumentException("No such beneficiary with name '" + name + "'");
	}

	/**
	 * Used to restore an allocated beneficiary. Should only be called by the repository responsible for reconstituting
	 * this account.
	 * @param beneficiary the beneficiary
	 */
	void restoreBeneficiary(Beneficiary beneficiary) {
		beneficiaries.add(beneficiary);
	}

	public String toString() {
		return "Number = '" + number + "', name = " + name + "', beneficiaries = " + beneficiaries;
	}
	
}