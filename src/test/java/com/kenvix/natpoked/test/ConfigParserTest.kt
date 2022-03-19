//--------------------------------------------------
// Class ConfigParserTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.utils.AppEnv
import kotlinx.serialization.decodeFromString
import net.mamoe.yamlkt.Yaml
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ConfigParserTest {
    @Test
    fun testPeerConfig() {
        println("ConfigParserTest")
        val configFile = Files.readString(Path.of(AppEnv.PeerTrustsFile))
        val peers = Yaml.decodeFromString<PeersConfig>(configFile)
        println(peers)
    }
}