package app.aaps.plugins.sync.nsShared

/**
 * Mode preset for Remote Control.
 * Maps user-friendly names to Therapy.kt keywords.
 *
 * There is no icon here on purpose. A drawable id is an Android resource and this class is shared -
 * it compiles for iOS and the JVM too - so the icon is the UI layer's decision and lives with the
 * adapters that draw it. Every mode shows the same placeholder today; the per-mode icons that were
 * planned, for whoever draws them: meals a restaurant, sport `directions_run`, sleep a bedtime icon,
 * gym `fitness_center`, meditation `self_improvement`, walking `directions_walk`, and one each for
 * stress, illness, stomach, work, school, bar, flight, fasting, low carb and stop.
 */
data class ModePreset(
    val id: String,
    val displayName: String,
    val therapyKeyword: String,  // Keyword that Therapy.kt searches for
    val defaultDurationMin: Int,
    val category: ModeCategory,
    val description: String = ""
)

enum class ModeCategory {
    MEAL,          // Triggers P1/P2 prebolus
    ACTIVITY,      // Sport, sleep (shown in MODES tab)
    CONTEXT_ONLY,  // Activity contexts (shown only in CONTEXTS tab)
    PHYSIO,        // Stress, illness
    CONTROL        // Stop, fasting
}

/**
 * All available modes for remote control.
 */
object ModePresets {
    
    val ALL_MODES = listOf(
        // ═══════════════════════════════════════════════════════════
        // MEAL MODES - Trigger P1/P2 Prebolus
        // ═══════════════════════════════════════════════════════════
        ModePreset(
            id = "breakfast",
            displayName = "Petit-déjeuner",
            therapyKeyword = "bfast",
            defaultDurationMin = 60,
            category = ModeCategory.MEAL,
            description = "Déclenche prébolus P1 et P2"
        ),
        ModePreset(
            id = "lunch",
            displayName = "Déjeuner",
            therapyKeyword = "lunch",
            defaultDurationMin = 60,
            category = ModeCategory.MEAL,
            description = "Déclenche prébolus P1 et P2"
        ),
        ModePreset(
            id = "dinner",
            displayName = "Dîner",
            therapyKeyword = "dinner",
            defaultDurationMin = 60,
            category = ModeCategory.MEAL,
            description = "Déclenche prébolus P1 et P2"
        ),
        ModePreset(
            id = "snack",
            displayName = "Collation",
            therapyKeyword = "snack",
            defaultDurationMin = 30,
            category = ModeCategory.MEAL,
            description = "Déclenche prébolus P1 et P2"
        ),
        ModePreset(
            id = "highcarb",
            displayName = "Repas riche",
            therapyKeyword = "highcarb",
            defaultDurationMin = 90,
            category = ModeCategory.MEAL,
            description = "Déclenche prébolus P1 et P2 renforcés"
        ),
        ModePreset(
            id = "meal",
            displayName = "Repas (général)",
            therapyKeyword = "meal",
            defaultDurationMin = 60,
            category = ModeCategory.MEAL,
            description = "Mode repas générique"
        ),
        
        // ═══════════════════════════════════════════════════════════
        // ACTIVITY MODES (shown in MODES tab)
        // ═══════════════════════════════════════════════════════════
        ModePreset(
            id = "sport",
            displayName = "Sport",
            therapyKeyword = "sport",
            defaultDurationMin = 120,
            category = ModeCategory.ACTIVITY,
            description = "Réduit SMB pendant l'activité"
        ),
        ModePreset(
            id = "sleep",
            displayName = "Sommeil",
            therapyKeyword = "sleep",
            defaultDurationMin = 480,
            category = ModeCategory.ACTIVITY,
            description = "Mode nuit sécurisé"
        ),
        
        // ═══════════════════════════════════════════════════════════
        // CONTEXT-ONLY (shown only in CONTEXTS tab, not in MODES)
        // ═══════════════════════════════════════════════════════════
        ModePreset(
            id = "cardio",
            displayName = "Cardio",
            therapyKeyword = "cardio",
            defaultDurationMin = 60,
            category = ModeCategory.CONTEXT_ONLY,
            description = "Course, vélo, natation"
        ),
        ModePreset(
            id = "strength",
            displayName = "Musculation",
            therapyKeyword = "strength",
            defaultDurationMin = 45,
            category = ModeCategory.CONTEXT_ONLY,
            description = "Exercices de force"
        ),
        ModePreset(
            id = "yoga",
            displayName = "Yoga",
            therapyKeyword = "yoga",
            defaultDurationMin = 60,
            category = ModeCategory.CONTEXT_ONLY,
            description = "Yoga, stretching"
        ),
        ModePreset(
            id = "walking",
            displayName = "Marche",
            therapyKeyword = "walking",
            defaultDurationMin = 30,
            category = ModeCategory.CONTEXT_ONLY,
            description = "Marche légère"
        ),
        
        // ═══════════════════════════════════════════════════════════
        // PHYSIOLOGICAL MODES
        // ═══════════════════════════════════════════════════════════
        ModePreset(
            id = "stress",
            displayName = "Stress",
            therapyKeyword = "stress",
            defaultDurationMin = 180,
            category = ModeCategory.PHYSIO,
            description = "Augmente basale (stress hormonal)"
        ),
        ModePreset(
            id = "illness",
            displayName = "Maladie",
            therapyKeyword = "illness",
            defaultDurationMin = 480,
            category = ModeCategory.PHYSIO,
            description = "Résistance à l'insuline accrue"
        ),
        ModePreset(
            id = "gastro",
            displayName = "Gastro",
            therapyKeyword = "gastro",
            defaultDurationMin = 480,
            category = ModeCategory.PHYSIO,
            description = "Troubles digestifs"
        ),
        ModePreset(
            id = "work_stress",
            displayName = "Stress travail",
            therapyKeyword = "work stress",
            defaultDurationMin = 240,
            category = ModeCategory.PHYSIO,
            description = "Stress professionnel"
        ),
        ModePreset(
            id = "exam_stress",
            displayName = "Stress examen",
            therapyKeyword = "exam stress",
            defaultDurationMin = 180,
            category = ModeCategory.PHYSIO,
            description = "Stress d'examen"
        ),
        ModePreset(
            id = "alcohol",
            displayName = "Alcool",
            therapyKeyword = "alcohol",
            defaultDurationMin = 360,
            category = ModeCategory.PHYSIO,
            description = "Consommation d'alcool"
        ),
        ModePreset(
            id = "travel",
            displayName = "Voyage",
            therapyKeyword = "travel",
            defaultDurationMin = 720,
            category = ModeCategory.PHYSIO,
            description = "Voyage / décalage horaire"
        ),
        
        // ═══════════════════════════════════════════════════════════
        // CONTROL MODES
        // ═══════════════════════════════════════════════════════════
        ModePreset(
            id = "fasting",
            displayName = "Jeûne",
            therapyKeyword = "fasting",
            defaultDurationMin = 720,
            category = ModeCategory.CONTROL,
            description = "Mode jeûne (réduit basale)"
        ),
        ModePreset(
            id = "lowcarb",
            displayName = "Low Carb",
            therapyKeyword = "lowcarb",
            defaultDurationMin = 180,
            category = ModeCategory.CONTROL,
            description = "Adaptation régime pauvre en glucides"
        ),
        ModePreset(
            id = "stop",
            displayName = "⛔ Annuler tout",
            therapyKeyword = "stop",
            defaultDurationMin = 0,
            category = ModeCategory.CONTROL,
            description = "Annule tous les modes actifs"
        )
    )
    
    
    /**
     * Find mode by therapy keyword (for parsing active modes).
     */
    fun findByKeyword(keyword: String): ModePreset? {
        return ALL_MODES.find { 
            it.therapyKeyword.equals(keyword, ignoreCase = true) 
        }
    }
}
