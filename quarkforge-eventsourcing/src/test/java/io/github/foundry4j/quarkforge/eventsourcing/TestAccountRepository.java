package io.github.foundry4j.quarkforge.eventsourcing;

import jakarta.enterprise.context.ApplicationScoped;

/// 测试用事件溯源仓储。
@ApplicationScoped
public class TestAccountRepository extends EventSourcedRepository<TestAccount> {

    @Override
    protected String aggregateType() {
        return "TestAccount";
    }

    @Override
    protected TestAccount newAggregate() {
        return new TestAccount();
    }
}
