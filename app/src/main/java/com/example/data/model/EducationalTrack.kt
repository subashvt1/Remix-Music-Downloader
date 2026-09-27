package com.example.data.model

data class EducationalTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val description: String,
    val durationText: String,
    val category: String,
    val directDownloadUrl: String,
    val previewArtworkUrl: String,
    val sourceLicense: String = "Public Domain / CC-BY"
)

object EducationalMusicCatalog {
    val sampleTracks = listOf(
        EducationalTrack(
            id = "beethoven_symphony_5",
            title = "Symphony No. 5 in C Minor, Op. 67 (Allegro)",
            artist = "Ludwig van Beethoven",
            album = "Classical Masterpieces Archive",
            description = "Iconic four-note motif representing fate knocking at the door. Educational study of sonata-allegro form.",
            durationText = "7:17",
            category = "Classical",
            directDownloadUrl = "https://upload.wikimedia.org/wikipedia/commons/4/4e/Ludwig_van_Beethoven_-_Symphony_No._5_in_C_minor%2C_Op._67_-_I._Allegro_con_brio.ogg",
            previewArtworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/Beethoven.jpg/600px-Beethoven.jpg",
            sourceLicense = "Public Domain / Musopen"
        ),
        EducationalTrack(
            id = "mozart_nachtmusik",
            title = "Eine kleine Nachtmusik (Serenade No. 13 in G)",
            artist = "Wolfgang Amadeus Mozart",
            album = "Vienna Serenade Collection",
            description = "A benchmark chamber piece demonstrating classical balance, rhythm, and melodic phrasing.",
            durationText = "5:56",
            category = "Classical",
            directDownloadUrl = "https://upload.wikimedia.org/wikipedia/commons/b/b2/Mozart_-_Eine_kleine_Nachtmusik_-_1._Allegro.ogg",
            previewArtworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Wolfgang-amadeus-mozart_1.jpg/600px-Wolfgang-amadeus-mozart_1.jpg",
            sourceLicense = "Public Domain / European Archive"
        ),
        EducationalTrack(
            id = "vivaldi_spring",
            title = "The Four Seasons: Spring (La Primavera - Allegro)",
            artist = "Antonio Vivaldi",
            album = "Baroque Concertos",
            description = "Pioneering program music with vivid baroque violin ornamentation imitating birdsong and murmuring streams.",
            durationText = "3:36",
            category = "Study",
            directDownloadUrl = "https://upload.wikimedia.org/wikipedia/commons/3/3c/Antonio_Vivaldi_-_The_Four_Seasons_-_Spring_-_1_Allegro.ogg",
            previewArtworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bd/Vivaldi.jpg/600px-Vivaldi.jpg",
            sourceLicense = "Public Domain / Wichita State Chamber"
        ),
        EducationalTrack(
            id = "beethoven_fur_elise",
            title = "Bagatelle No. 25 in A Minor (Für Elise)",
            artist = "Ludwig van Beethoven",
            album = "Piano Masterworks for Students",
            description = "One of the most famous solo piano melodies in music history, ideal for melodic and harmonic analysis.",
            durationText = "2:57",
            category = "Educational",
            directDownloadUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/F%C3%BCr_Elise.ogg",
            previewArtworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/Beethoven.jpg/600px-Beethoven.jpg",
            sourceLicense = "Public Domain"
        ),
        EducationalTrack(
            id = "bach_brandenburg_3",
            title = "Brandenburg Concerto No. 3 (BWV 1048 - Allegro)",
            artist = "Johann Sebastian Bach",
            album = "Baroque Polyphony Anthology",
            description = "Exemplary Baroque counterpoint with nine solo string parts weaving intricate musical tapestries.",
            durationText = "5:20",
            category = "Educational",
            directDownloadUrl = "https://upload.wikimedia.org/wikipedia/commons/8/86/Johann_Sebastian_Bach_-_Brandenburg_Concerto_No._3_in_G_major%2C_BWV_1048_-_1._Allegro.ogg",
            previewArtworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6a/Johann_Sebastian_Bach.jpg/600px-Johann_Sebastian_Bach.jpg",
            sourceLicense = "Public Domain / Advent Chamber Orchestra"
        ),
        EducationalTrack(
            id = "apollo_11_audio",
            title = "Apollo 11 Moon Landing: 'The Eagle Has Landed'",
            artist = "NASA Historical Audio Archive",
            album = "Milestones of Human Exploration",
            description = "Original July 20, 1969 mission audio of Neil Armstrong announcing touchdown on the lunar surface.",
            durationText = "1:45",
            category = "Lecture",
            directDownloadUrl = "https://upload.wikimedia.org/wikipedia/commons/1/12/Apollo_11_-_Eagle_has_landed.ogg",
            previewArtworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/98/Aldrin_Apollo_11_original.jpg/600px-Aldrin_Apollo_11_original.jpg",
            sourceLicense = "Public Domain / NASA"
        )
    )
}
