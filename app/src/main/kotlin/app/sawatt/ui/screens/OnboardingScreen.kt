package app.sawatt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sawatt.AppContainer
import app.sawatt.R
import app.sawatt.ui.components.ProfileForm
import app.sawatt.ui.theme.SawattColors

/** First launch: asks for the rider's profile, saved on the phone when they continue. */
@Composable
fun OnboardingScreen(container: AppContainer) {
    // A draft, so that nothing is saved (and the first launch is not over) until the button.
    var draft by remember { mutableStateOf(container.settings.profile.value) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                stringResource(R.string.onboarding_text),
                style = MaterialTheme.typography.bodyMedium,
                color = SawattColors.TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            ProfileForm(draft, onChange = { transform -> draft = transform(draft) }, modifier = Modifier.padding(bottom = 16.dp))
        }
        Button(
            onClick = { container.settings.updateProfile { draft } },
            colors = ButtonDefaults.buttonColors(containerColor = SawattColors.Accent, contentColor = SawattColors.OnAccent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
        ) {
            Text(stringResource(R.string.onboarding_start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
