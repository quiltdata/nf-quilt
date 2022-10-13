/*
 * Copyright 2022, Quilt Data Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nextflow.quilt3.nio

import nextflow.quilt3.jep.QuiltPackage
import nextflow.quilt3.jep.QuiltParser
import java.nio.file.Files
import java.nio.file.FileSystem
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.ProviderMismatchException
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.nio.file.WatchService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Global
import nextflow.Session
import nextflow.quilt3.QuiltOpts
import nextflow.quilt3.jep.QuiltParser
/**
 * Implements Path interface for Quilt storage
 *
 * @author Ernest Prabhakar <ernest@quiltdata.io>
 */

@Slf4j
@CompileStatic
public final class QuiltPath implements Path {
    private final QuiltFileSystem filesystem
    private final QuiltParser parsed
    private final String[] paths

    public QuiltPath(QuiltFileSystem filesystem, QuiltParser parsed) {
        this.filesystem = filesystem
        this.parsed = parsed
        this.paths = parsed.paths()
        log.debug "Creating QuiltPath[$parsed]@$filesystem"
    }

    public String bucket() {
        parsed.bucket()
    }

    public String pkg_name() {
        parsed.pkg_name()
    }

    public String sub_paths() {
        parsed.path()
    }

    public QuiltPackage pkg() {
        isAbsolute() ? QuiltPackage.ForParsed(parsed) : null
    }

    public String file_key() {
        parsed.toString()
    }

    Path localPath() {
        Path pkgPath = pkg().packageDest()
        assert pkgPath
        Paths.get(pkgPath.toUriString(), sub_paths())
    }

    public boolean deinstall() {
        Path path = localPath()
        log.debug "QuiltPath.deinstall: $path"
        return Files.delete(path)
    }

    @Override
    FileSystem getFileSystem() {
        return filesystem
    }

    @Override
    boolean isAbsolute() {
        filesystem && pkg_name() != ""
    }

    boolean isJustPackage() {
        !parsed.hasPath()
    }

    QuiltPath getJustPackage() {
        if ( isJustPackage() ) return this
        QuiltParser pkg_parsed = QuiltParser.ForBarePath(parsed.toPackageString())
        new QuiltPath(filesystem, pkg_parsed)
    }

    @Override
    Path getRoot() {
        isAbsolute() ? getJustPackage() : null
    }

    @Override
    Path getFileName() {
        log.debug "QuiltFileSystem.getFileName`[${this}]: paths=$paths"
        isJustPackage() ? this : new QuiltPath(filesystem, parsed.lastPath()) // IF DIRECTORY
    }

    @Override
    Path getParent() {
        log.debug "${this}.getParent: ${paths}`"
        new QuiltPath(filesystem, parsed.dropPath())
    }

    @Override
    int getNameCount() {
        paths.size()
    }

    @Override
    Path getName(int index) {
        throw new UnsupportedOperationException("Operation 'getName' is not supported by QuiltPath")
        //new QuiltPath(filesystem, pkg_name, sub_paths[0,index], options)
    }

    @Override
    Path subpath(int beginIndex, int endIndex) {
        throw new UnsupportedOperationException("Operation 'subpath' is not supported by QuiltPath")
        //final sub = sub_paths[beginIndex,endIndex].join(SEP)
        //new QuiltPath(filesystem, pkg_name, sub, options)
    }

    @Override
    boolean startsWith(Path other) {
        startsWith(other.toString())
    }

    @Override
    boolean startsWith(String other) {
        toString().startsWith(other)
    }

    @Override
    boolean endsWith(Path other) {
        endsWith(other.toString())
    }

    @Override
    boolean endsWith(String other) {
        toString().endsWith(other)
    }

    @Override
    int compareTo(Path other) {
        return toString() <=> other.toString()
    }

    @Override
    Path normalize() {
        log.debug "`normalize` should elide '..' paths"
        return this
    }

    @Override
    QuiltPath resolve(Path other) {
        if( other.class != QuiltPath )
            throw new ProviderMismatchException()

        final that = (QuiltPath)other
        if( other.isAbsolute() )
            return that
        throw new UnsupportedOperationException("Operation 'resolve'[$that] is not supported by QuiltPath[$this]")
        //new QuiltPath(filesystem, pkg_name, other.toString(), options)
    }

    @Override
    QuiltPath resolve(String other) {
        log.debug "$this: `resolve[$other]`"
        new QuiltPath(filesystem, parsed.appendPath(other))
    }

    @Override
    Path resolveSibling(Path other) {
        throw new UnsupportedOperationException("Operation 'resolveSibling'[$other] is not supported by QuiltPath")
        //new QuiltPath(filesystem, pkg_name, other.toString(), options)
    }

    @Override
    Path resolveSibling(String other) {
        throw new UnsupportedOperationException("Operation 'resolveSibling'[$other] is not supported by QuiltPath")
        //new QuiltPath(filesystem, pkg_name, other, options)
    }

// Oct-12 06:46:17.554 [Actor Thread 5] DEBUG nextflow.file.FilePorter - Unable to determine stage file integrity: source=quilt-example#package=examples%2fhurdat; target=/Users/quilt/Documents/GitHub/nf-quilt/work/stage/84/946926f92c10a0acd210d79b3b9edd
// Operation 'relativize'[/var/folders/rr/hp1w0hxd07lgq1y8k9dmnrwr0000gq/T/QuiltPackage2346698145953900107/quilt_example_examples_hurdat/scripts/build.py] is not supported by QuiltPath

    @Override
    Path relativize(Path other) {
        String base = pkg().toString()
        String file = (other instanceof QuiltPath) ? ((QuiltPath)other).localPath() : other.toString()
        log.debug "relativize[$base] in [$file]"
        int i = file.indexOf(base)
        if (i<1) {
            throw new UnsupportedOperationException("other[$file] does not contain package[$base]")
        }

        String tail = file.substring(i + base.size())
        if (tail.size() > 0 && tail[0] == '/') tail = tail.substring(1) // drop "/"
        log.debug "tail[$i] -> $tail"
        QuiltParser p = new QuiltParser(parsed.bucket(), parsed.pkg_name(), tail, parsed.options)
        log.debug "QuiltParser:$p"
        new QuiltPath(filesystem, p)
    }

    @Override
    String toString() {
        parsed.toString()
    }

    String toUriString() {
        parsed.toUriString()
    }

    @Override
    URI toUri() {
        return new URI(toUriString())
    }

    @Override
    Path toAbsolutePath() {
        if(isAbsolute()) return this
        throw new UnsupportedOperationException("Operation 'toAbsolutePath' is not supported by QuiltPath")
    }

    @Override
    Path toRealPath(LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Operation 'toRealPath' is not supported by QuiltPath")
    }

    @Override
    File toFile() {
        throw new UnsupportedOperationException("Operation 'toFile' is not supported by QuiltPath")
    }

    @Override
    WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("Operation 'register' is not supported by QuiltPath")
    }

    @Override
    WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException("Operation 'register' is not supported by QuiltPath")
    }

    @Override
    Iterator<Path> iterator() {
      throw new UnsupportedOperationException("Operation 'iterator' is not supported by QuiltPath")
    }

}
