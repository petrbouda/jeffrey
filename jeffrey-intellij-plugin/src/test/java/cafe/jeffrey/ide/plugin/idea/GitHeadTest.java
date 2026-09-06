/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.ide.plugin.idea;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Pure file-reading tests for the checkout reader — no IDE fixture and no git binary needed. */
public class GitHeadTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void readsALooseBranchRef() throws IOException {
        Path project = repository();
        write(project.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        write(project.resolve(".git/refs/heads/main"), COMMIT + "\n");

        GitHead.Checkout checkout = GitHead.read(project.toString());

        assertEquals("main", checkout.branch());
        assertEquals(COMMIT, checkout.commit());
    }

    @Test
    public void readsANestedBranchName() throws IOException {
        Path project = repository();
        write(project.resolve(".git/HEAD"), "ref: refs/heads/feature/ide-tools\n");
        write(project.resolve(".git/refs/heads/feature/ide-tools"), COMMIT + "\n");

        assertEquals("feature/ide-tools", GitHead.read(project.toString()).branch());
    }

    @Test
    public void fallsBackToPackedRefsWhenTheBranchHasNoLooseFile() throws IOException {
        Path project = repository();
        write(project.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        write(project.resolve(".git/packed-refs"),
                "# pack-refs with: peeled fully-peeled sorted\n"
                        + COMMIT + " refs/heads/main\n"
                        + "ffffffffffffffffffffffffffffffffffffffff refs/tags/v1\n"
                        + "^eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\n");

        GitHead.Checkout checkout = GitHead.read(project.toString());

        assertEquals("main", checkout.branch());
        assertEquals(COMMIT, checkout.commit());
    }

    @Test
    public void reportsADetachedHeadAsACommitWithoutABranch() throws IOException {
        Path project = repository();
        write(project.resolve(".git/HEAD"), COMMIT + "\n");

        GitHead.Checkout checkout = GitHead.read(project.toString());

        assertNull(checkout.branch());
        assertEquals(COMMIT, checkout.commit());
    }

    @Test
    public void followsTheGitdirPointerOfAWorktree() throws IOException {
        Path project = temp.newFolder("worktree").toPath();
        Path realGitDir = temp.newFolder("main-checkout", ".git", "worktrees", "wt").toPath();
        write(project.resolve(".git"), "gitdir: " + realGitDir + "\n");
        write(realGitDir.resolve("HEAD"), "ref: refs/heads/main\n");
        write(realGitDir.resolve("refs/heads/main"), COMMIT + "\n");

        assertEquals(COMMIT, GitHead.read(project.toString()).commit());
    }

    @Test
    public void answersUnknownRatherThanGuessing() throws IOException {
        assertEquals(GitHead.Checkout.UNKNOWN, GitHead.read(null));
        assertEquals(GitHead.Checkout.UNKNOWN, GitHead.read(" "));
        // A directory that is not a repository at all.
        assertEquals(GitHead.Checkout.UNKNOWN, GitHead.read(temp.newFolder("plain").getAbsolutePath()));
    }

    @Test
    public void refusesARefThatIsNotACommitId() throws IOException {
        Path project = repository();
        write(project.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        // Half-written by a concurrent checkout: a branch, but no commit to report.
        write(project.resolve(".git/refs/heads/main"), "0123456\n");

        GitHead.Checkout checkout = GitHead.read(project.toString());

        assertEquals("main", checkout.branch());
        assertNull(checkout.commit());
    }

    private Path repository() throws IOException {
        File project = temp.newFolder("project-" + temp.getRoot().list().length);
        Files.createDirectories(project.toPath().resolve(".git"));
        return project.toPath();
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
