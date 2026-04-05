package com.fireflybot.application.port.out

import com.fireflybot.domain.model.Category

interface CategoryRepository {
    fun getCategories(): List<Category>
}
