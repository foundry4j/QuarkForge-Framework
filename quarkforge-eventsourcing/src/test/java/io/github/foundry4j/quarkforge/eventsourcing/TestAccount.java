package io.github.foundry4j.quarkforge.eventsourcing;

import java.math.BigDecimal;

/// ## TestAccount
/// 测试用聚合根：开户 + 存款，状态完全由事件重放得到。
public class TestAccount extends AggregateRoot {

    private String accountNo;

    private BigDecimal balance = BigDecimal.ZERO;

    public static TestAccount open(String accountNo, BigDecimal amount) {
        TestAccount account = new TestAccount();
        account.apply(new AccountOpened(accountNo, amount));
        return account;
    }

    public void deposit(BigDecimal amount) {
        apply(new MoneyDeposited(amount));
    }

    public String accountNo() {
        return accountNo;
    }

    public BigDecimal balance() {
        return balance;
    }

    @Override
    protected void mutate(DomainEvent event) {
        switch (event) {
            case AccountOpened opened -> {
                accountNo = opened.accountNo();
                balance = opened.amount();
            }
            case MoneyDeposited deposited -> balance = balance.add(deposited.amount());
            default -> throw new IllegalStateException("未知事件: " + event.getClass().getName());
        }
    }
}
