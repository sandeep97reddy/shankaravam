---
name: jetpack-compose-performance
description: Instructions and architectural patterns for building ultra-fluid 60/120fps Jetpack Compose screens, offline-first Room reactive flows, and Vishnu Chakra vector animations.
---

# Jetpack Compose & Performance Guide for Festival App

This skill guides the agent in writing fast, buttery-smooth (60/120 FPS) Jetpack Compose screens with Material 3 for the festival organizer app.

## 1. Sudarshana Chakra Splash Animation

To render the temple-themed opening screen with the rotating Vishnu Sudarshana Chakra:
- Use `androidx.compose.animation.core.rememberInfiniteTransition` with `Animatable`.
- Easing: Linear for rotation, or smooth acceleration-deceleration.
- Ensure the transition to the main content occurs within **1.2 to 1.5 seconds** so organizers at busy counters are never delayed.

```kotlin
@Composable
fun VishnuChakraLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    tint: Color = TempleGold
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ChakraSpin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Angle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        // Subtle radial aura/glow behind the chakra
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(TempleGold.copy(alpha = 0.35f), Color.Transparent)
                )
            )
        }
        // Rotating Chakra Icon
        Icon(
            painter = painterResource(id = R.drawable.ic_sudarshana_chakra),
            contentDescription = "Sudarshana Chakra",
            tint = tint,
            modifier = Modifier
                .fillMaxSize(0.75f)
                .graphicsLayer { rotationZ = angle }
        )
    }
}
```

---

## 2. Room Single Source of Truth & Zero-Jank UI

### Reactive StateFlow Pattern
- Always return `Flow<List<DonationEntity>>` directly from Room DAOs.
- In the ViewModel, convert this to `StateFlow` with `SharingStarted.WhileSubscribed(5000)`:

```kotlin
val donations: StateFlow<List<Donation>> = donationRepository
    .observeDonationsForEvent(eventId)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
```

### Fluid List Animations
When donations are added rapidly at the counter, the list must smoothly animate new items into place without frame drops:

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    state = listState,
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    items(
        items = donationList,
        key = { it.id } // ALWAYS provide a stable key!
    ) { donation ->
        DonationCard(
            donation = donation,
            modifier = Modifier.animateItem() // Compose 1.7+ smooth insertion/reorder
        )
    }
}
```

### Dashboard Performance (`derivedStateOf`)
Avoid recalculating collected totals or filtering lists during recompositions:

```kotlin
val totalCollected by remember(donations) {
    derivedStateOf {
        donations.filter { it.status == DonationStatus.CONFIRMED || it.status == DonationStatus.RECEIVED }
            .sumOf { it.amount }
    }
}
```
