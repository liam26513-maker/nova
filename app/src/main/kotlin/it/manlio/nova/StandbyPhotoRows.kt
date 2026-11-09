package it.manlio.nova

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.feature.home.StandbyPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Scelta delle foto dello StandBy con il selettore di Android (nessun permesso sulla galleria), e il tasto per toglierle. */
@Composable
internal fun StandbyPhotoRows() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var count by remember { mutableIntStateOf(StandbyPhotos.list(context).size) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(StandbyPhotos.MAX)) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch { count = withContext(Dispatchers.IO) { StandbyPhotos.replace(context, uris) } }
        }
    SettingsRow(
        stringResource(R.string.standby_photos_pick),
        summary =
            if (count > 0) stringResource(R.string.standby_photos_count, count) else stringResource(R.string.standby_photos_none),
        showDivider = true,
        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
    if (count > 0) {
        SettingsRow(
            stringResource(R.string.standby_photos_clear),
            showDivider = true,
            onClick = {
                StandbyPhotos.clear(context)
                count = 0
            },
        )
    }
}
