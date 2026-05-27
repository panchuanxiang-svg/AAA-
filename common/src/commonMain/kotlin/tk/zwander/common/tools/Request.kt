package tk.zwander.common.tools

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import dev.whyoleg.cryptography.DelicateCryptographyApi
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import tk.zwander.common.data.BinaryFileInfo
import tk.zwander.common.data.FetchResult
import tk.zwander.common.data.exception.VersionCheckException
import tk.zwander.common.data.exception.VersionException
import tk.zwander.common.data.exception.VersionMismatchException
import tk.zwander.common.exceptions.DownloadError
import tk.zwander.common.exceptions.NoBinaryFileError
import tk.zwander.common.util.CrossPlatformBugsnag
import tk.zwander.common.util.dataNode
import tk.zwander.common.util.firstDataElementDataByTagName
import tk.zwander.common.util.firstElementByTagName
import tk.zwander.common.util.invoke
import tk.zwander.common.util.isAccessoryModel
import tk.zwander.common.util.textNode
import tk.zwander.samloaderkotlin.resources.MR
import kotlin.time.ExperimentalTime

object Request {
    fun getLogicCheck(input: String, nonce: String): String {
        if (input.length < 16) {
            return ""
        }

        return buildString {
            nonce.forEach { char ->
                append(input[char.code and 0xf])
            }
        }
    }

    suspend fun performBinaryInformRetry(
        fw: String,
        model: String,
        region: String,
        imeiSerial: String,
    ): Pair<String, Document> {
        val splitImeiSerial = imeiSerial.split("\n")
            .flatMap { it.split(";") }
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var latestRequest = ""
        var latestResult: Document = Ksoup.parse("")
        var latestError: Throwable? = null

        splitImeiSerial.forEachIndexed { index, imei ->
            latestRequest = createBinaryInform(fw, model, region, FusClient.getNonce())

            if (index % 10 == 0) {
                delay(1000)
            }

            latestResult = try {
                val response = FusClient.makeReq(
                    FusClient.Request.BINARY_INFORM,
                    latestRequest,
                    imei,
                )

                Ksoup.parse(response)
            } catch (e: Throwable) {
                latestError = e
                e.printStackTrace()
                return@forEachIndexed
            }

            latestResult.let { result ->
                val status = result.firstElementByTagName("FUSBody")
                    ?.firstElementByTagName("Results")
                    ?.firstElementByTagName("Status")
                    ?.text()

                println("Status for IMEI $imei: $status")

                if (status == "200" ||
                    status == "202" ||
                    status == "500" ||
                    status == "S00") {
                    return latestRequest to result
                }
            }
        }

        latestError?.let { throw it }

        return latestRequest to latestResult
    }

    private fun createBinaryInform(
        fw: String,
        model: String,
        region: String,
        nonce: String,
    ): String {
        val logicCheck = try {
            getLogicCheck(fw, nonce)
        } catch (e: Throwable) {
            e.printStackTrace()
            ""
        }

        val xml = xml("FUSMsg") {
            "FUSHdr" {
                textNode("ProtoVer", "1")
                textNode("SessionID", "0")
                textNode("MsgID", "1")
            }

            "FUSBody" {
                "Put" {
                    textNode("CmdID", "1")

                    dataNode("ACCESS_MODE", "1")
                    dataNode("BINARY_NATURE", "1")
                    dataNode("REQUEST_TYPE", "2")
                    dataNode("LOGIC_CHECK", logicCheck.trim())
                    dataNode("BINARY_SW_VERSION", fw)

                    dataNode("DEVICE_SN_NUMBER", "")
                    dataNode("BINARY_LOCAL_CODE", region)
                    dataNode("BINARY_MODEL_NAME", model)

                    "CLIENT_LANGUAGE" {
                        textNode("Type", "String")
                        textNode("Type", "ISO 3166-1-alpha-3")
                        textNode("Data", "1033")
                    }

                    val (cc, mcc, mnc) = when (region) {
                        "EUX" -> Triple("DE", "262", "01")
                        "EUY" -> Triple("RS", "220", "01")
                        "TGY" -> Triple("HK", "454", "03")
                        "KOO" -> Triple("KR", "450", "05")
                        else -> Triple(null, null, null)
                    }

                    cc?.let { dataNode("DEVICE_CC_CODE", it) }
                    mcc?.let { dataNode("MCC_NUM", it) }
                    mnc?.let { dataNode("MNC_NUM", it) }
                }

                "Get" {
                    textNode("CmdID", "2")
                    "BINARY_SW_VERSION"()
                }
            }
        }

        return xml.toString(PrintOptions(singleLineTextElements = true))
    }

    suspend fun retrieveBinaryFileInfo(
        fw: String,
        model: String,
        region: String,
        imeiSerial: String,
        onFinish: suspend (String) -> Unit,
        onVersionException: (suspend (VersionException, BinaryFileInfo?) -> Unit)? = null,
        shouldReportError: suspend (Exception) -> Boolean = { true },
    ): BinaryFileInfo? {
        val result = getBinaryFile(fw, model, region, imeiSerial)

        val (info, error, output, requestBody) = result

        if (error is VersionException && onVersionException != null) {
            onVersionException(error, info)
            return null
        } else if (error != null) {
            onFinish("${error.message ?: MR.strings.error()}\n\n${output}")

            if (result.isReportableCode() &&
                !output.contains("Incapsula") &&
                error !is CancellationException &&
                shouldReportError(error) &&
                !isAccessoryModel(model)
            ) {
                CrossPlatformBugsnag.notify(
                    DownloadError(requestBody, output, error),
                )
            }
        }

        return info
    }

    private suspend fun getBinaryFile(
        fw: String,
        model: String,
        region: String,
        imeiSerial: String,
    ): FetchResult.GetBinaryFileResult {
        val (request, responseXml) = try {
            performBinaryInformRetry(
                fw.uppercase(),
                model,
                region,
                imeiSerial,
            )
        } catch (e: Exception) {
            CrossPlatformBugsnag.notify(e)

            return FetchResult.GetBinaryFileResult(
                error = e,
                rawOutput = "",
                requestBody = "",
            )
        }

        try {
            val status = responseXml
                .firstElementByTagName("FUSBody")
                ?.firstElementByTagName("Results")
                ?.firstElementByTagName("Status")
                ?.text()

            if (status != "200" &&
                status != "202" &&
                status != "500" &&
                status != "S00") {
                return FetchResult.GetBinaryFileResult(
                    error = Exception("Bad return status: $status"),
                    rawOutput = responseXml.toString(),
                    requestBody = request,
                    responseCode = status,
                )
            }

            val fileName = responseXml
                .firstDataElementDataByTagName("MODEL_PATH")

            val fileSize = responseXml
                .firstDataElementDataByTagName("MODEL_SIZE")
                ?.toLongOrNull()

            val crc = responseXml
                .firstDataElementDataByTagName("CRC")
                ?.toLongOrNull()

            val logicValue = responseXml
                .firstDataElementDataByTagName("LOGIC_VALUE_FACTORY")

            if (fileName.isNullOrBlank() || fileSize == null) {
                return FetchResult.GetBinaryFileResult(
                    error = NoBinaryFileError(),
                    rawOutput = responseXml.toString(),
                    requestBody = request,
                    responseCode = status,
                )
            }

            val v4Key = try {
                responseXml.extractV4Key()
            } catch (_: Throwable) {
                null
            }

            return FetchResult.GetBinaryFileResult(
                info = BinaryFileInfo(
                    path = fileName,
                    fileName = fileName.substringAfterLast("/"),
                    size = fileSize,
                    crc32 = crc,
                    v4Key = v4Key,
                    fwVer = fw,
                    modelType = model,
                    logicVal = logicValue ?: "",
                ),
                rawOutput = responseXml.toString(),
                requestBody = request,
                responseCode = status,
            )
        } catch (e: Exception) {
            return FetchResult.GetBinaryFileResult(
                error = e,
                rawOutput = responseXml.toString(),
                requestBody = request,
            )
        }
    }

    @OptIn(DelicateCryptographyApi::class)
    fun Document.extractV4Key(): Pair<ByteArray, String>? {
        return try {
            val encryptedKey = firstDataElementDataByTagName("BINARY_BYTE_SIZE")
                ?: return null

            val logicValue = firstDataElementDataByTagName("LOGIC_VALUE_FACTORY")
                ?: return null

            CryptUtils.decryptNonce(encryptedKey) to logicValue
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}