package io.tickonomics.git.hooks

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Given-When-Then tests for {@link GitHooksPlugin}, exercised via GradleRunner
 * against a throwaway project. The harness applies the plugin the same way the
 * root build does and redirects nothing: the task installs into the project's
 * own {@code .git/hooks} (Copy creates the directory tree), so the real repo's
 * hooks are never touched.
 */
class GitHooksPluginSpec extends Specification {

    @TempDir
    Path projectDir

    private Path hookSources
    private Path hookDest
    private GradleRunner runner

    void setup() {
        hookSources = projectDir.resolve('scripts/git-hooks')
        hookDest = projectDir.resolve('.git/hooks')
        Files.createDirectories(hookSources)
        // Written WITHOUT the executable bit on purpose: the task must set the
        // bit itself, not merely inherit it from the source (false-positive guard).
        writeHook('pre-commit', '#!/usr/bin/env bash\necho pre-commit\n')
        writeHook('pre-push', '#!/usr/bin/env bash\necho pre-push\n')

        Files.writeString(projectDir.resolve('settings.gradle'), "rootProject.name = 'harness'\n")
        Files.writeString(projectDir.resolve('build.gradle'),
            "plugins { id 'java'; id 'io.tickonomics.git-hooks' }\n")

        runner = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .forwardOutput()
    }

    def "copies both hook sources into .git/hooks as executable"() {
        when: 'the install task runs'
        def result = gradle('installGitHooks').build()

        then: 'both hooks are present and executable'
        result.task(':installGitHooks').outcome == TaskOutcome.SUCCESS
        Files.isRegularFile(hookDest.resolve('pre-commit'))
        Files.isRegularFile(hookDest.resolve('pre-push'))
        Files.isExecutable(hookDest.resolve('pre-commit'))
        Files.isExecutable(hookDest.resolve('pre-push'))
    }

    def "is up-to-date on a second unchanged run"() {
        given: 'the task has already run once'
        gradle('installGitHooks').build()

        when: 'it runs again with no changes'
        def result = gradle('installGitHooks').build()

        then: 'Gradle reports it up-to-date'
        result.task(':installGitHooks').outcome == TaskOutcome.UP_TO_DATE
    }

    def "re-copies a hook when its source changes"() {
        given: 'the task has already run once'
        gradle('installGitHooks').build()

        when: 'a source hook is modified'
        writeHook('pre-commit', '#!/usr/bin/env bash\necho changed\n')
        def result = gradle('installGitHooks').build()

        then: 'the destination reflects the change'
        result.task(':installGitHooks').outcome == TaskOutcome.SUCCESS
        Files.readString(hookDest.resolve('pre-commit')).contains('changed')
    }

    def "runs automatically as a dependency of build"() {
        when: 'the build lifecycle task runs'
        def result = gradle('build').build()

        then: 'installGitHooks was part of the task graph'
        result.task(':installGitHooks') != null
        result.task(':installGitHooks').outcome in [TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE]
    }

    def "is configuration-cache compatible"() {
        when: 'the task runs with the configuration cache enabled'
        def result = gradle('installGitHooks', '--configuration-cache').build()

        then: 'it succeeds and the hooks are installed'
        result.task(':installGitHooks').outcome == TaskOutcome.SUCCESS
        Files.isExecutable(hookDest.resolve('pre-commit'))
    }

    private GradleRunner gradle(String... tasks) {
        runner.withArguments(tasks as List)
    }

    private void writeHook(String name, String body) {
        Files.writeString(hookSources.resolve(name), body)
    }
}
