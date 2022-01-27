package com.kenvix.natpoked.contacts

enum class RequestTypes(val typeId: Int) {
    UNKNOWN(0),

    ACTION_KEEP_ALIVE                      (0x1000_0000),
    ACTION_ECHO                            (0x1000_0020),
    ACTION_CONNECT_PEER                    (0x1000_0060),

    MESSAGE_KEEP_ALIVE                     (0x2000_0000),
    MESSAGE_HANDSHAKE                      (0x2000_0010),
    MESSAGE_TEST_LIVENESS                  (0x2000_0030),
    MESSAGE_ECHO                           (0x2000_0020),

    MESSAGE_GET_PEER_INFO                  (0x2000_0030),
    MESSAGE_GET_PEER_INFO_NOCHECK          (0x2000_0031),

    MESSAGE_CONNECT_PEER                   (0x2000_0060),
}