package me.mudkip.moememos.data.repository

import me.mudkip.moememos.data.model.RelationType
import org.junit.Assert.assertEquals
import org.junit.Test

class RelationTypeMappingTest {
    @Test
    fun fromString_acceptsBackendEnumNames() {
        assertEquals(RelationType.REFERENCE, relationTypeFromString("RELATION_TYPE_REFERENCE"))
        assertEquals(RelationType.COMMENT, relationTypeFromString("RELATION_TYPE_COMMENT"))
        assertEquals(RelationType.ATTACHMENT, relationTypeFromString("RELATION_TYPE_ATTACHMENT"))
        assertEquals(RelationType.UNSPECIFIED, relationTypeFromString("RELATION_TYPE_UNSPECIFIED"))
    }

    @Test
    fun fromString_acceptsShortNamesAndIsCaseInsensitive() {
        assertEquals(RelationType.REFERENCE, relationTypeFromString("reference"))
        assertEquals(RelationType.COMMENT, relationTypeFromString("Comment"))
        assertEquals(RelationType.ATTACHMENT, relationTypeFromString("ATTACHMENT"))
    }

    @Test
    fun fromString_nullOrUnknownFallsBackToUnspecified() {
        assertEquals(RelationType.UNSPECIFIED, relationTypeFromString(null))
        assertEquals(RelationType.UNSPECIFIED, relationTypeFromString(""))
        assertEquals(RelationType.UNSPECIFIED, relationTypeFromString("whatever"))
    }

    @Test
    fun toString_roundTripsAllTypes() {
        for (type in RelationType.entries) {
            assertEquals(type, relationTypeFromString(relationTypeToString(type)))
        }
    }
}
