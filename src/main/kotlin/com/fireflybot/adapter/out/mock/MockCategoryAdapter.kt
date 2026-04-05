package com.fireflybot.adapter.out.mock

import com.fireflybot.application.port.out.CategoryRepository
import com.fireflybot.domain.model.Category

class MockCategoryAdapter : CategoryRepository {
    override fun getCategories(): List<Category> = MockData.categories
}
