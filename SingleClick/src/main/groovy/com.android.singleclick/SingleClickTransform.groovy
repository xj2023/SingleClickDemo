package com.android.singleclick ;

import com.android.annotations.NonNull
import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.singleclick.concurrent.Worker
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES

class SingleClickTransform extends Transform {

    private final Worker worker

    private final String FILE_SEP = File.separator;

    private final FileTime ZERO = FileTime.fromMillis(0)

    SingleClickTransform() {
        this.worker = Schedulers.IO()
    }

    @Override
    String getName() {
        return this.class.getSimpleName()
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return true
    }

    @Override
    void transform(@NonNull TransformInvocation transformInvocation) {
        println "--------------- ${getName()} visit start --------------- "
        Collection<TransformInput> inputs = transformInvocation.inputs
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        boolean isIncremental = transformInvocation.isIncremental()
        println "${getName()} isIncremental ： $isIncremental"
        if (!isIncremental) {
            outputProvider.deleteAll()
        }
        //遍历inputs
        def jarTime = 0
        def fileTime = 0
        def startTime = System.currentTimeMillis();
        inputs.each { TransformInput input ->
            //遍历directoryInputs
            def fileStartTime = System.currentTimeMillis()
            doIncrementalTransform(isIncremental, input.directoryInputs, outputProvider)
            def fileEndTime = System.currentTimeMillis()
            fileTime = fileTime + (fileEndTime - fileStartTime)

            def jarStartTime = System.currentTimeMillis()
            doJarInputTransform(isIncremental, input.jarInputs, outputProvider)
            def jarEndTime = System.currentTimeMillis()
            jarTime = jarTime + (jarEndTime - jarStartTime)
        }
        def jarTimeCost = (jarTime) / 1000
        def fileTimeCost = (fileTime) / 1000
        worker.await();
        println "${getName()} jarTime cost ： $jarTimeCost s"
        println "${getName()} fileTimeCost cost ： $fileTimeCost s"

        def costTime = System.currentTimeMillis() - startTime;
        println(getName() + " cost " + costTime + "ms")
        println "--------------- ${getName()} visit end --------------- "
    }

    private void doIncrementalTransform(boolean isIncremental, Collection<DirectoryInput> directoryInputs, TransformOutputProvider outputProvider) {
        directoryInputs.each { DirectoryInput directoryInput ->
            if (isIncremental) {
                File dest = outputProvider.getContentLocation(directoryInput.getName(),
                        directoryInput.getContentTypes(), directoryInput.getScopes(),
                        Format.DIRECTORY);
                FileUtils.forceMkdir(dest);
                String srcDirPath = directoryInput.getFile().getAbsolutePath();
                String destDirPath = dest.getAbsolutePath();
                Map<File, Status> fileStatusMap = directoryInput.getChangedFiles();
                for (Map.Entry<File, Status> changedFile : fileStatusMap.entrySet()) {
                    Status status = changedFile.getValue();
                    File inputFile = changedFile.getKey();
                    String destFilePath = inputFile.getAbsolutePath().replace(srcDirPath, destDirPath);
                    File destFile = new File(destFilePath);
                    switch (status) {
                        case Status.NOTCHANGED:
                            break;
                        case Status.REMOVED:
                            //  System.out.println(" SingleClickTransform removeFile  " + destFilePath);
                            if (destFile.exists()) {
                                destFile.delete();
                            }
                            break;
                        case Status.ADDED:
                        case Status.CHANGED:
                            // System.out.println(" SingleClickTransform changeFile  " + destFilePath);
                            try {
                                FileUtils.touch(destFile);
                            } catch (Exception e) {
                                //maybe mkdirs fail for some strange reason, try again.
                                //  FileUtils.forceMkdirParent(destFile);
                            }
                            transformSingleFile(inputFile, destFile, srcDirPath);
                            break;
                    }
                }
            } else {
                transformDir(directoryInput, outputProvider);
            }
        }
    }

    private void transformSingleFile(
            final File inputFile, final File outputFile, final String srcBaseDir) {
        worker.execute(new Runnable() {
            @Override
            void run() {
                weaveSingleClassToFile(inputFile, outputFile, srcBaseDir);
            }
        })
    }

    final void weaveSingleClassToFile(File inputFile, File outputFile, String inputBaseDir) throws IOException {
        if (!inputBaseDir.endsWith(FILE_SEP)) inputBaseDir = inputBaseDir + FILE_SEP;
        boolean isWeavableClass = isWeavableClass(inputFile.getAbsolutePath().replace(inputBaseDir, "").replace(FILE_SEP, "."));
        if (isWeavableClass) {
            FileUtils.touch(outputFile);
            InputStream inputStream = new FileInputStream(inputFile);
            byte[] bytes = weaveSingleClassToByteArray(inputStream);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(bytes);
            fos.close();
            inputStream.close();
        } else {
            if (inputFile.isFile()) {
                FileUtils.touch(outputFile);
                FileUtils.copyFile(inputFile, outputFile);
            }
        }
    }


    private void transformDir(DirectoryInput directoryInput, TransformOutputProvider outputProvider) throws IOException {
        if (directoryInput.file.isDirectory()) {
            //列出目录所有文件（包含子文件夹，子文件夹内文件）
            directoryInput.file.eachFileRecurse { File file ->
                def name = file.name
                //   println '----------- deal with "class" file <' + file.name + '> -----------'
                if (checkClassFile(name)) {
                    //  ASM  基于事件形式  类似解析XML的SAX 逐步解析
                    ClassReader classReader = new ClassReader(file.bytes)
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
                    ClassVisitor cv = new TimeClassVisitor(classWriter)
                    classReader.accept(cv, EXPAND_FRAMES)
                    //  ASM  基于对象形式   类似解析XML的DOM  全部加载到内存中解析后创建对象
                    //     byte[] code = modifyClass(classWriter.toByteArray())
                    byte[] code = classWriter.toByteArray()
                    FileOutputStream fos = new FileOutputStream(
                            file.parentFile.absolutePath + File.separator + name)
                    fos.write(code)
                    fos.close()
                    //    println '----------- copy with "class" file <' + file.parentFile.absolutePath + File.separator + name + '> -----------'
                }
            }
        }
        //处理完输入文件之后，要把输出给下一个任务
        def dest = outputProvider.getContentLocation(directoryInput.name,
                directoryInput.contentTypes, directoryInput.scopes,
                Format.DIRECTORY)
        FileUtils.copyDirectory(directoryInput.file, dest)
    }

    private void doJarInputTransform(boolean isIncremental, Collection<JarInput> jarInputs, TransformOutputProvider outputProvider) {
        for (JarInput jarInput : jarInputs) {
            Status status = jarInput.getStatus();
            File dest = outputProvider.getContentLocation(jarInput.getFile().getAbsolutePath(), jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
            if (isIncremental) {
                switch (status) {
                    case Status.NOTCHANGED:
                        break;
                    case Status.ADDED:
                    case Status.CHANGED:
                        transformJar(jarInput.getFile(), dest, status);
                        break;
                    case Status.REMOVED:
                        if (dest.exists()) {
                            FileUtils.forceDelete(dest);
                        }
                        break;
                }
            } else {
                transformJar(jarInput.getFile(), dest, status);
            }
        }
    }


    private void transformJar(final File srcJar, final File destJar, Status status) {
        worker.execute(new Runnable() {
            @Override
            void run() {
                weaveJar(srcJar, destJar)
            }
        })
    }

    private void weaveJar(final File inputJar, final File outputJar) {
        ZipFile inputZip = new ZipFile(inputJar)
        ZipOutputStream outputZip = new ZipOutputStream(new BufferedOutputStream(
                Files.newOutputStream(outputJar.toPath())));
        Enumeration<? extends ZipEntry> inEntries = inputZip.entries();
        while (inEntries.hasMoreElements()) {
            ZipEntry entry = inEntries.nextElement();
            InputStream originalFile =
                    new BufferedInputStream(inputZip.getInputStream(entry));
            ZipEntry outEntry = new ZipEntry(entry.getName());
            byte[] newEntryContent;
            // separator of entry name is always '/', even in windows
            if (!isWeavableClass(outEntry.getName().replace("/", "."))) {
                newEntryContent = IOUtils.toByteArray(originalFile);
            } else {
                newEntryContent = weaveSingleClassToByteArray(originalFile);
            }
            CRC32 crc32 = new CRC32();
            crc32.update(newEntryContent);
            outEntry.setCrc(crc32.getValue());
            outEntry.setMethod(ZipEntry.STORED);
            outEntry.setSize(newEntryContent.length);
            outEntry.setCompressedSize(newEntryContent.length);
            outEntry.setLastAccessTime(ZERO);
            outEntry.setLastModifiedTime(ZERO);
            outEntry.setCreationTime(ZERO);
            outputZip.putNextEntry(outEntry);
            outputZip.write(newEntryContent);
            outputZip.closeEntry();
        }
        outputZip.flush();
        outputZip.close();
    }

    private static byte[] weaveSingleClassToByteArray(InputStream inputStream) throws IOException {
        ClassReader classReader = new ClassReader(inputStream);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
        ClassVisitor cv = new TimeClassVisitor(classWriter)
        classReader.accept(cv, EXPAND_FRAMES)
        return classWriter.toByteArray();
    }

    /**
     * 检查class文件是否需要处理
     * @param fileName
     * @return
     */
    private static boolean checkClassFile(String className) {
        return className.endsWith(".class") && !className.contains("R\$") && !className.contains("R.class") && !className.contains("BuildConfig.class");
    }
}