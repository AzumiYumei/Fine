package com.fine.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class RetryPolicyTest {
    @Test public void onlyTransientFailuresAreRetried() {
        for (int code : new int[]{-1,408,429,500,503}) assertTrue(RetryPolicy.retryable(code));
        for (int code : new int[]{400,401,403,404,416}) assertFalse(RetryPolicy.retryable(code));
        assertEquals(4, RetryPolicy.ATTEMPTS);
    }
    @Test public void rangesAndSignaturesStayDistinct() {
        assertNotEquals(RetryPolicy.segmentId("https://a/v",0,10), RetryPolicy.segmentId("https://a/v",10,10));
        assertNotEquals(RetryPolicy.segmentId("https://a/v?a=1",0,-1), RetryPolicy.segmentId("https://a/v?a=2",0,-1));
    }
}
