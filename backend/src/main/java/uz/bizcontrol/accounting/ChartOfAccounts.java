package uz.bizcontrol.accounting;

import java.util.List;

/**
 * Standard chart-of-accounts template + canonical account codes.
 * Codes are referenced directly by the posting logic so they must stay stable.
 */
public final class ChartOfAccounts {

    private ChartOfAccounts() {}

    // Canonical account codes
    public static final String CASH               = "1000";
    public static final String BANK               = "1010";
    public static final String ACCOUNTS_RECEIVABLE = "1100";
    public static final String INVENTORY          = "1200";
    public static final String ACCOUNTS_PAYABLE   = "2000";
    public static final String TAX_PAYABLE        = "2100";
    /** Accrued labor/overhead/transport applied to production (credited on completion). */
    public static final String PRODUCTION_COSTS_PAYABLE = "2200";
    /** Logistics/customs/broker obligations — separate from supplier debt so the two never mix. */
    public static final String LOGISTICS_PAYABLE        = "2300";
    public static final String EQUITY             = "3000";
    public static final String SALES_REVENUE      = "4000";
    public static final String COGS               = "5000";
    /** Cost of raw materials lost as production waste/scrap. */
    public static final String PRODUCTION_WASTE   = "5100";
    public static final String OPERATING_EXPENSES = "6000";
    public static final String CUSTOMS_EXPENSES   = "6100";
    public static final String LOGISTICS_EXPENSES = "6200";
    public static final String FX_DIFFERENCE      = "7000";

    public record Def(String code, String name, String type, String normalBalance) {}

    public static final List<Def> TEMPLATE = List.of(
        new Def(CASH,                "Cash",                "ASSET",     "DEBIT"),
        new Def(BANK,                "Bank",                "ASSET",     "DEBIT"),
        new Def(ACCOUNTS_RECEIVABLE, "Accounts Receivable", "ASSET",     "DEBIT"),
        new Def(INVENTORY,           "Inventory",           "ASSET",     "DEBIT"),
        new Def(ACCOUNTS_PAYABLE,    "Accounts Payable",    "LIABILITY", "CREDIT"),
        new Def(TAX_PAYABLE,         "Tax Payable",         "LIABILITY", "CREDIT"),
        new Def(PRODUCTION_COSTS_PAYABLE, "Production Costs Payable", "LIABILITY", "CREDIT"),
        new Def(LOGISTICS_PAYABLE,   "Logistics Payable",   "LIABILITY", "CREDIT"),
        new Def(EQUITY,              "Owner Equity",        "EQUITY",    "CREDIT"),
        new Def(SALES_REVENUE,       "Sales Revenue",       "REVENUE",   "CREDIT"),
        new Def(COGS,                "Cost of Goods Sold",  "EXPENSE",   "DEBIT"),
        new Def(PRODUCTION_WASTE,    "Production Waste",     "EXPENSE",   "DEBIT"),
        new Def(OPERATING_EXPENSES,  "Operating Expenses",  "EXPENSE",   "DEBIT"),
        new Def(CUSTOMS_EXPENSES,    "Customs Expenses",    "EXPENSE",   "DEBIT"),
        new Def(LOGISTICS_EXPENSES,  "Logistics Expenses",  "EXPENSE",   "DEBIT"),
        new Def(FX_DIFFERENCE,       "FX Difference",       "EXPENSE",   "DEBIT")
    );
}
