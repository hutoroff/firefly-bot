package com.fireflybot.application.port.out

import com.fireflybot.domain.model.Tag

interface TagRepository {
    fun getTags(): List<Tag>
}
