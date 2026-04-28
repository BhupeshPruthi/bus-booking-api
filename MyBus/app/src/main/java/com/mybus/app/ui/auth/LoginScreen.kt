package com.mybus.app.ui.auth

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.mybus.app.BuildConfig
import com.mybus.app.R
import com.mybus.app.ui.components.AppModalBottomSheet
import com.mybus.app.ui.components.LocalAnimatedBottomSheetDismiss
import java.security.MessageDigest

private const val GOOGLE_SIGN_IN_CANCELLED = 12501
private const val GOOGLE_SIGN_IN_SIGN_IN_FAILED = 12500

/** Play Services / GMS connection: API not available in this context (legacy Sign-In). */
private const val GOOGLE_SIGN_IN_NOT_IMPLEMENTED = 9

private fun isMissingWebClientId(id: String): Boolean {
    val t = id.trim()
    return t.isEmpty() ||
        t.contains("YOUR_", ignoreCase = true) ||
        !t.endsWith(".apps.googleusercontent.com")
}

private const val NULL_ID_TOKEN_HELP =
    "No ID token from Google. Use OAuth client type “Web application” for serverClientId. " +
        "Set google.web.client.id in local.properties and GOOGLE_CLIENT_ID in the API .env, then rebuild."

private fun maskClientId(id: String): String {
    val trimmed = id.trim()
    if (trimmed.length <= 22) return trimmed
    return "${trimmed.take(12)}...${trimmed.takeLast(31)}"
}

private fun ByteArray.toColonSha1(): String {
    return joinToString(":") { "%02X".format(it) }
}

@Suppress("DEPRECATION")
private fun Context.installedSigningSha1(): String? {
    return runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            packageInfo.signatures
        }
        val certificate = signatures?.firstOrNull()?.toByteArray() ?: return null
        MessageDigest.getInstance("SHA-1").digest(certificate).toColonSha1()
    }.getOrNull()
}

private fun oauthSetupHint(
    context: Context,
    webClientId: String,
): String {
    val sha1 = context.installedSigningSha1() ?: "unknown"
    return "Google Cloud setup required: create/update an Android OAuth client with package " +
        "${context.packageName} and SHA-1 $sha1. Keep google.web.client.id and Railway " +
        "GOOGLE_CLIENT_ID set to the Web application client ID ${maskClientId(webClientId)}. " +
        "If the app is installed from Google Play, use the Play App Signing SHA-1; if it is " +
        "installed from this Mac, use the SHA-1 shown here."
}

private fun googleSignInErrorMessage(
    context: Context,
    webClientId: String,
    error: ApiException,
): String {
    val base = when (error.statusCode) {
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Google Sign-In is not configured for this installed app."
        GOOGLE_SIGN_IN_NOT_IMPLEMENTED ->
            "Google Play services cannot handle Google Sign-In on this device."
        GOOGLE_SIGN_IN_SIGN_IN_FAILED ->
            "Google Sign-In failed."
        else ->
            "Google Sign-In error (${error.statusCode}): ${error.message ?: "unknown"}."
    }
    val shouldAddSetupHint =
        error.statusCode == CommonStatusCodes.DEVELOPER_ERROR ||
            error.statusCode == GOOGLE_SIGN_IN_SIGN_IN_FAILED ||
            error.message.orEmpty().contains("developer console", ignoreCase = true) ||
            error.message.orEmpty().contains("not set up correctly", ignoreCase = true) ||
            error.message.orEmpty().contains("not setup correctly", ignoreCase = true)
    return if (shouldAddSetupHint) {
        "$base ${oauthSetupHint(context, webClientId)}"
    } else {
        base
    }
}

@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onGoogleSignIn: (String) -> Unit,
    onSignInError: (String) -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    val webClientId = remember(context) {
        val fromGradle = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (fromGradle.isNotEmpty()) fromGradle else context.getString(R.string.default_web_client_id)
    }

    val googleSignInClient = remember(context, webClientId) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode != Activity.RESULT_OK) {
                if (result.resultCode != Activity.RESULT_CANCELED) {
                    onSignInError("Google sign-in failed (code ${result.resultCode}). Try again.")
                }
                return@rememberLauncherForActivityResult
            }
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                onGoogleSignIn(idToken)
            } else {
                onSignInError(NULL_ID_TOKEN_HELP)
            }
        } catch (e: ApiException) {
            if (e.statusCode == GOOGLE_SIGN_IN_CANCELLED) {
                return@rememberLauncherForActivityResult
            }
            onSignInError(googleSignInErrorMessage(context, webClientId, e))
        } catch (e: Throwable) {
            onSignInError("Sign-in error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    LaunchedEffect(uiState.error) {
        val err = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = err,
            duration = SnackbarDuration.Long
        )
    }

    fun launchGoogleSignIn() {
        if (isMissingWebClientId(webClientId)) {
            onSignInError(
                "Missing Google Web client ID. In MyBus/local.properties add " +
                    "google.web.client.id=...apps.googleusercontent.com, then rebuild."
            )
            return
        }
        val gms = GoogleApiAvailability.getInstance()
        val gmsResult = gms.isGooglePlayServicesAvailable(context)
        if (gmsResult != ConnectionResult.SUCCESS) {
            val fixable = gms.isUserResolvableError(gmsResult)
            onSignInError(
                if (fixable) {
                    "Google Play services must be updated (code $gmsResult)."
                } else {
                    "Google Play services unavailable on this device (code $gmsResult)."
                }
            )
            return
        }

        googleSignInClient.signOut().addOnCompleteListener {
            try {
                googleLauncher.launch(googleSignInClient.signInIntent)
            } catch (e: Throwable) {
                onSignInError("Sign-in error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    val sheetTextMuted = Color.White.copy(alpha = 0.65f)
    val sheetTextDim = Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        AppModalBottomSheet(
            onDismissRequest = onDismiss,
        ) {
            val animatedDismiss = LocalAnimatedBottomSheetDismiss.current
            BackHandler(onBack = animatedDismiss)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 0.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Spacer(modifier = Modifier.size(48.dp))
                    Text(
                        text = stringResource(R.string.login_title, stringResource(R.string.app_name)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = animatedDismiss,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Jai Shri Ram",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sheetTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Jai Balaji Maharaj",
                    style = MaterialTheme.typography.bodyMedium,
                    color = sheetTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))

                Spacer(modifier = Modifier.height(20.dp))

                if (uiState.error != null) {
                    Text(
                        text = uiState.error,
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        onClearError()
                        launchGoogleSignIn()
                    },
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFC790D),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFFC790D).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.login_sign_in_google),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(R.string.login_legal_prefix),
                    style = MaterialTheme.typography.bodySmall,
                    color = sheetTextDim,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.login_terms),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            uriHandler.openUri(context.getString(R.string.url_terms))
                        },
                    )
                    Text(
                        text = stringResource(R.string.login_and),
                        style = MaterialTheme.typography.bodySmall,
                        color = sheetTextDim,
                    )
                    Text(
                        text = stringResource(R.string.login_privacy_policy),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            uriHandler.openUri(context.getString(R.string.url_privacy))
                        },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .fillMaxWidth(),
        )
    }
}
