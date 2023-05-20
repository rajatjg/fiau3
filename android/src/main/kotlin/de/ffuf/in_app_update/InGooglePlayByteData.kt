package de.ffuf.in_app_update
import com.google.gson.annotations.SerializedName
import org.json.JSONObject;

data class InGooglePlayByteData (
    @SerializedName("bytes_downloaded")
    val bytesDownloaded: String? = null,
    @SerializedName("total_bytes_to_download")
    val totalBytesToDownload: String? = null,
    @SerializedName("current_state")
    val currentState: String? = null
)