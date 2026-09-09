package com.wuji.kidora.ai.mcp.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConnectivityTools 单测。
 *
 * @author liudy
 */
class ConnectivityToolsTest {

    private final ConnectivityTools tools = new ConnectivityTools();

    @Test
    void echoPing_includesMessageAndTs() {
        String json = tools.echoPing("hi");
        assertTrue(json.contains("\"echo\":\"hi\""));
        assertTrue(json.contains("\"ts\":"));
    }
}
