package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.Callable;

class RetryerAssert {

    private final Retryer retryer;
    private Object result;

    private RetryerAssert(Retryer retryer) {
        this.retryer = retryer;
    }

    static RetryerAssert assertThatRetryer(Retryer retryer) {
        return new RetryerAssert(retryer);
    }

    <T> RetryExceptionAssert throwsRetryExceptionCalling(Callable<T> callable) {
        var retryException = catchThrowableOfType(() -> retryer.call(callable), RetryException.class);
        return new RetryExceptionAssert(retryException);
    }

    <T> RetryerAssert completesSuccessfullyCalling(Callable<T> callable) {
        try {
            result = retryer.call(callable);
        } catch (RetryException e) {
            fail("Received unexpected RetryException");
        } catch (InterruptedException e) {
            fail("Received unexpected InterruptedException");
        }
        return this;
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    <T> RetryerAssert hasResult(T expected) {
        assertThat(result).isEqualTo(expected);
        return this;
    }
}
