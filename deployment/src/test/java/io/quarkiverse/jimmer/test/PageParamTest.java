package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.repo.PageParam;

class PageParamTest {
    @Test
    void distinguishesZeroBasedIndexesFromOneBasedPageNumbers() {
        assertEquals(PageParam.byIndex(0, 20), PageParam.byNo(1, 20));
        assertEquals(PageParam.byIndex(2, 20), PageParam.byNo(3, 20));
    }

    @Test
    void rejectsInvalidPagesBeforeBuildingAQuery() {
        assertThrows(IllegalArgumentException.class, () -> PageParam.byNo(0, 20));
        assertThrows(IllegalArgumentException.class, () -> PageParam.byNo(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> PageParam.byIndex(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> PageParam.byNo(1, 0));
        assertThrows(IllegalArgumentException.class, () -> PageParam.byIndex(0, 0));
    }
}
