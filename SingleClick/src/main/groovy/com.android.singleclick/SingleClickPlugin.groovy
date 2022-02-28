package com.android.singleclick

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.AppExtension

class SingleClickPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        println("apply ${this.class.getSimpleName()}")
        // 注册 transform
        project.extensions.getByType(AppExtension).registerTransform(new SingleClickTransform())
    }
}