package cz.zorn.iruploader.db

data class FirmwareDesc(
    var id: Long,
    var name: String?,
    var author: String?,
    var version: String?,
    var ts: String,
)