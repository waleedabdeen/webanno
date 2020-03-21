/*
 * Copyright 2018
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.dao.export.exporters;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.CORRECTION_USER;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.INITIAL_CAS_PSEUDO_USER;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.PROJECT_TYPE_AUTOMATION;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.PROJECT_TYPE_CORRECTION;
import static de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportRequest.FORMAT_AUTO;
import static de.tudarmstadt.ukp.clarin.webanno.model.Mode.ANNOTATION;
import static de.tudarmstadt.ukp.clarin.webanno.model.Mode.CORRECTION;
import static java.lang.Math.ceil;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.io.FileUtils.copyFileToDirectory;
import static org.apache.commons.io.FileUtils.copyInputStreamToFile;
import static org.apache.commons.io.FileUtils.forceDelete;
import static org.apache.commons.io.FileUtils.forceMkdir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.UIMAException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ImportExportService;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportRequest;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExportTaskMonitor;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectExporter;
import de.tudarmstadt.ukp.clarin.webanno.api.export.ProjectImportRequest;
import de.tudarmstadt.ukp.clarin.webanno.api.format.FormatSupport;
import de.tudarmstadt.ukp.clarin.webanno.export.model.ExportedAnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.export.model.ExportedProject;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.logging.LogMessage;
import de.tudarmstadt.ukp.clarin.webanno.tsv.WebAnnoTsv3FormatSupport;

@Component
public class AnnotationDocumentExporter
    implements ProjectExporter
{
    private static final String ANNOTATION_ORIGINAL_FOLDER = "/annotation/";
    private static final String ANNOTATION_AS_SERIALISED_CAS = "annotation_ser";
    private static final String ANNOTATION_CAS_FOLDER = "/" + ANNOTATION_AS_SERIALISED_CAS + "/";
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final DocumentService documentService;
    private final AnnotationSchemaService schemaService;
    private final UserDao userRepository;
    private final ImportExportService importExportService;
    
    @Autowired
    public AnnotationDocumentExporter(DocumentService aDocumentService,
            AnnotationSchemaService aSchemaService, UserDao aUserRepository,
            ImportExportService aImportExportService)
    {
        documentService = aDocumentService;
        schemaService = aSchemaService;
        userRepository = aUserRepository;
        importExportService = aImportExportService;
    }

    @Override
    public List<Class<? extends ProjectExporter>> getExportDependencies()
    {
        return asList(SourceDocumentExporter.class);
    }
    
    @Override
    public List<Class<? extends ProjectExporter>> getImportDependencies()
    {
        return asList(SourceDocumentExporter.class);
    }
    
    @Override
    public void exportData(ProjectExportRequest aRequest, ProjectExportTaskMonitor aMonitor,
            ExportedProject aExProject, File aStage)
        throws UIMAException, ClassNotFoundException, IOException
    {
        exportAnnotationDocuments(aMonitor, aRequest.getProject(), aExProject);
        exportAnnotationDocumentContents(aRequest, aMonitor, aExProject, aStage);
    }
    
    private void exportAnnotationDocuments(ProjectExportTaskMonitor aMonitor, Project aProject,
            ExportedProject aExProject)
    {
        List<ExportedAnnotationDocument> annotationDocuments = new ArrayList<>();

        for (AnnotationDocument annotationDocument : documentService
                .listAnnotationDocuments(aProject)) {
            ExportedAnnotationDocument exAnnotationDocument = new ExportedAnnotationDocument();
            exAnnotationDocument.setName(annotationDocument.getName());
            exAnnotationDocument.setState(annotationDocument.getState());
            exAnnotationDocument.setUser(annotationDocument.getUser());
            exAnnotationDocument.setTimestamp(annotationDocument.getTimestamp());
            exAnnotationDocument.setSentenceAccessed(annotationDocument.getSentenceAccessed());
            exAnnotationDocument.setCreated(annotationDocument.getCreated());
            exAnnotationDocument.setUpdated(annotationDocument.getUpdated());
            annotationDocuments.add(exAnnotationDocument);
        }

        aExProject.setAnnotationDocuments(annotationDocuments);
    }

    private void exportAnnotationDocumentContents(ProjectExportRequest aRequest,
            ProjectExportTaskMonitor aMonitor, ExportedProject aExProject, File aStage)
        throws UIMAException, ClassNotFoundException, IOException
    {
        Project project = aRequest.getProject();
        
        // The export process may store project-related information in this context to ensure it
        // is looked up only once during the bulk operation and the DB is not hit too often.
        Map<Pair<Project, String>, Object> bulkOperationContext = new HashMap<>();
        
        List<SourceDocument> documents = documentService.listSourceDocuments(project);
        int i = 1;
        int initProgress = aMonitor.getProgress();

        // Create a map containing the annotation documents for each source document. Doing this
        // as one DB access before the main processing to avoid hammering the DB in the loops 
        // below.
        Map<SourceDocument, List<AnnotationDocument>> srcToAnnIdx = documentService
                .listAnnotationDocuments(project).stream()
                .collect(groupingBy(doc -> doc.getDocument(), toList()));

        // Cache user lookups to avoid constantly hitting the database
        LoadingCache<String, User> usersCache = Caffeine.newBuilder()
                .build(key -> userRepository.get(key));

        for (SourceDocument srcDoc : documents) {
            //
            // Export initial CASes
            //
            
            // The initial CAS must always be exported to ensure that the converted source document
            // will *always* have the state it had at the time of the initial import. We we do have
            // a reliably initial CAS and instead lazily convert whenever an annotator starts
            // annotating, then we could end up with two annotators having two different versions of
            // their CAS e.g. if there was a code change in the reader component that affects its
            // output.

            // If the initial CAS does not exist yet, it must be created before export.
            if (!documentService.existsInitialCas(srcDoc)) {
                documentService.createOrReadInitialCas(srcDoc);
            }
            
            File targetDir = new File(aStage, ANNOTATION_CAS_FOLDER + srcDoc.getName());
            forceMkdir(targetDir);
            
            File initialCasFile = documentService.getCasFile(srcDoc, INITIAL_CAS_PSEUDO_USER);
            
            copyFileToDirectory(initialCasFile, targetDir);
            
            log.info("Exported annotation document content for user [" + INITIAL_CAS_PSEUDO_USER
                    + "] for source document [" + srcDoc.getId() + "] in project ["
                    + project.getName() + "] with id [" + project.getId() + "]");

            //
            // Export per-user annotation document
            // 
            
            // Determine which format to use for export
            String formatId = FORMAT_AUTO.equals(aRequest.getFormat()) ? srcDoc.getFormat()
                    : aRequest.getFormat();
            
            FormatSupport format = importExportService.getWritableFormatById(formatId)
                    .orElseGet(() -> {
                        FormatSupport fallbackFormat = new WebAnnoTsv3FormatSupport();
                        aMonitor.addMessage(LogMessage.error(this,"Annotation: [%s] No writer "
                                + "found for original format [%s] - exporting as [%s] "
                                + "instead.",
                                srcDoc.getName(), formatId, fallbackFormat.getName()));
                        return fallbackFormat;
                    });

            // Export annotations from regular users
            for (AnnotationDocument annDoc : srcToAnnIdx.get(srcDoc)) {
                
                // copy annotation document only for existing users and the state of the 
                // annotation document is not NEW/IGNORE
                if (
                        usersCache.get(annDoc.getUser()) != null && 
                        !annDoc.getState().equals(AnnotationDocumentState.NEW) && 
                        !annDoc.getState().equals(AnnotationDocumentState.IGNORE)
                ) {
                    File annSerDir = new File(
                            aStage.getAbsolutePath() + ANNOTATION_CAS_FOLDER
                                    + srcDoc.getName());
                    File annDocDir = new File(aStage.getAbsolutePath()
                            + ANNOTATION_ORIGINAL_FOLDER + srcDoc.getName());

                    forceMkdir(annSerDir);
                    forceMkdir(annDocDir);

                    File annSerFile = documentService.getCasFile(srcDoc, annDoc.getUser());

                    File annFile = null;
                    if (annSerFile.exists()) {
                        annFile = importExportService.exportAnnotationDocument(srcDoc,
                                annDoc.getUser(), format, annDoc.getUser(), ANNOTATION, false,
                                bulkOperationContext);
                    }
                    
                    if (annSerFile.exists()) {
                        copyFileToDirectory(annSerFile, annSerDir);
                        copyFileToDirectory(annFile, annDocDir);
                        forceDelete(annFile);
                    }
                    
                    log.info("Exported annotation document content for user ["
                            + annDoc.getUser() + "] for source document ["
                            + srcDoc.getId() + "] in project [" + project.getName() + "] with id ["
                            + project.getId() + "]");
                }
            }
            
            // Special handling for the virtual CORRECTION_USER data used in automation and
            // correction type projects.
            if (
                    PROJECT_TYPE_AUTOMATION.equals(project.getMode()) || 
                    PROJECT_TYPE_CORRECTION.equals(project.getMode())
            ) {
                File corrSerFile = documentService.getCasFile(srcDoc, CORRECTION_USER);
                if (corrSerFile.exists()) {
                    // Copy CAS - this is used when importing the project again
                    // Util WebAnno 3.4.x, the CORRECTION_USER CAS was exported to 'curation' and
                    // 'curation_ser'.
                    // Since WebAnno 3.5.x, the CORRECTION_USER CAS is exported to 'annotation' and
                    // 'annotation_ser'.
                    File curationSerDir = new File(
                            aStage + ANNOTATION_AS_SERIALISED_CAS + srcDoc.getName());
                    forceMkdir(curationSerDir);
                    copyFileToDirectory(corrSerFile, curationSerDir);

                    // Copy secondary export format for convenience - not used during import
                    File curationDir = new File(
                            aStage + ANNOTATION_ORIGINAL_FOLDER + srcDoc.getName());
                    forceMkdir(curationDir);
                    File corrFile = importExportService.exportAnnotationDocument(srcDoc,
                            CORRECTION_USER, format, CORRECTION_USER, CORRECTION);
                    copyFileToDirectory(corrFile, curationDir);
                    forceDelete(corrFile);
                }
            }
            
            aMonitor.setProgress(initProgress + (int) ceil(((double) i) / documents.size() * 80.0));
            i++;
        }
    }
    
    @Override
    public void importData(ProjectImportRequest aRequest, Project aProject,
            ExportedProject aExProject, ZipFile aZip)
        throws Exception
    {
        Map<String, SourceDocument> nameToDoc = documentService
                .listSourceDocuments(aProject).stream()
                .collect(toMap(SourceDocument::getName, identity()));
        
        importAnnotationDocuments(aExProject, aProject, nameToDoc);
        importAnnotationDocumentContents(aZip, aProject, nameToDoc);
    }
    
    /**
     * Create {@link de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument} from exported
     * {@link ExportedAnnotationDocument}
     * 
     * @param aExProject
     *            the imported project.
     * @param aProject
     *            the project.
     * @throws IOException
     *             if an I/O error occurs.
     */
    private void importAnnotationDocuments(ExportedProject aExProject, Project aProject,
            Map<String, SourceDocument> aNameToDoc)
        throws IOException
    {
        for (ExportedAnnotationDocument exAnnotationDocument : aExProject
                .getAnnotationDocuments()) {
            AnnotationDocument annotationDocument = new AnnotationDocument();
            annotationDocument.setName(exAnnotationDocument.getName());
            annotationDocument.setState(exAnnotationDocument.getState());
            annotationDocument.setProject(aProject);
            annotationDocument.setUser(exAnnotationDocument.getUser());
            annotationDocument.setTimestamp(exAnnotationDocument.getTimestamp());
            annotationDocument.setDocument(aNameToDoc.get(exAnnotationDocument.getName()));
            annotationDocument.setSentenceAccessed(exAnnotationDocument.getSentenceAccessed());
            annotationDocument.setCreated(exAnnotationDocument.getCreated());
            annotationDocument.setUpdated(exAnnotationDocument.getUpdated());
            documentService.createAnnotationDocument(annotationDocument);
        }
    }
    
    /**
     * copy annotation documents (serialized CASs) from the exported project
     * 
     * @param zip
     *            the ZIP file.
     * @param aProject
     *            the project.
     * @throws IOException
     *             if an I/O error occurs.
     */
    @SuppressWarnings("rawtypes")
    private void importAnnotationDocumentContents(ZipFile zip, Project aProject,
            Map<String, SourceDocument> aNameToDoc)
        throws IOException
    {
        
        for (Enumeration zipEnumerate = zip.entries(); zipEnumerate.hasMoreElements();) {
            ZipEntry entry = (ZipEntry) zipEnumerate.nextElement();

            // Strip leading "/" that we had in ZIP files prior to 2.0.8 (bug #985)
            String entryName = ProjectExporter.normalizeEntryName(entry);

            if (!entryName.startsWith(ANNOTATION_AS_SERIALISED_CAS + "/") ||
                !entryName.endsWith(".ser")) {
                continue;
            }

            String fileName = entryName.replace(ANNOTATION_AS_SERIALISED_CAS + "/", "");

            if (fileName.trim().isEmpty()) {
                continue;
            }

            // the user annotated the document is file name minus extension (anno1.ser)
            String username = FilenameUtils.getBaseName(fileName).replace(".ser", "");

            // name of the annotation document
            fileName = fileName.replace(FilenameUtils.getName(fileName), "").replace("/", "");
            SourceDocument sourceDocument = aNameToDoc.get(fileName);
            File annotationFilePath = documentService.getCasFile(sourceDocument, username);

            copyInputStreamToFile(zip.getInputStream(entry), annotationFilePath);

            log.info("Imported annotation document content for user [" + username
                    + "] for source document [" + sourceDocument.getId() + "] in project ["
                    + aProject.getName() + "] with id [" + aProject.getId() + "]");
        }
    }
}
