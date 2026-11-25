plugins {
    id("blackdoor.java-conventions")
}

description = "ProtocolBridge implementation backed by PacketEvents (GPL-3.0). The only module that may import PacketEvents."

dependencies {
    api(project(":blackdoor-protocol"))
    // Provided at runtime by the separately installed PacketEvents plugin (OD-6).
    compileOnly(libs.packetevents.api)
    testImplementation(libs.packetevents.api)
}
