package org.l2x6.editorconfig.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.l2x6.editorconfig.core.FormatException;
import org.l2x6.editorconfig.core.Location;
import org.l2x6.editorconfig.core.Resource;
import org.l2x6.editorconfig.core.Violation;
import org.l2x6.editorconfig.core.ViolationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormattingHandler
    implements ViolationHandler
{

    private static final Logger log = LoggerFactory.getLogger(FormattingHandler.class);
    private EditableDocument currentFile;

    private int editedFileCount = 0;
    private int processedFileCount = 0;
    private List<Violation> violations = new ArrayList<Violation>();
    private final boolean backup;
    private final boolean backupSuffix;
    public FormattingHandler( boolean backup, boolean backupSuffix )
    {
        super();
        this.backup = backup;
        this.backupSuffix = backupSuffix;
    }

    @Override
    public ReturnState endFile()
    {
        try
        {
            if (violations.isEmpty()) {
                log.debug( "No formatting violations found in file " + currentFile );
                backupAndStoreIfNeeded();
                return ReturnState.FINISHED;
            } else {
                log.debug( "Fixing " + violations.size() + " formatting "+ (violations.size() == 1 ? "violation" : "violations") +" in file " + currentFile );
                editedFileCount++;

                /* We want to allow only one edit per line to avoid edit conflicts
                 * We should actually check that the edits do not span over multiple lines, which we do not ATM */
                Set<Integer> linesEdited = new HashSet<>();
                boolean recheckNeeded = false;
                for ( Violation violation : violations )
                {
                    Location loc = violation.getLocation();
                    final Integer line = Integer.valueOf( loc.getLine() );
                    if (!linesEdited.contains( line )) {
                        int lineStartOffset = currentFile.findLineStart( loc.getLine() );
                        int editOffset = lineStartOffset + loc.getColumn() - 1;
                        final Edit fix = violation.getFix();
                        log.debug( "About to perform '"+ fix.getMessage() +"' at line "+ loc.getLine() + ", column "+ loc.getColumn() +", lineStartOffset "+ lineStartOffset + ", editOffset "+ editOffset);
                        fix.fix( currentFile, editOffset );;
                        linesEdited.add( line );
                    } else {
                        recheckNeeded = true;
                    }
                }
                if (recheckNeeded) {
                    return ReturnState.RECHECK;
                }
                else {
                    backupAndStoreIfNeeded();
                    return ReturnState.FINISHED;
                }
            }
        }
        catch ( IOException e )
        {
            throw new FormatException( "Could not format file "+ currentFile, e );
        } finally {
            processedFileCount++;
            this.currentFile = null;
            this.violations.clear();
        }
    }

    private void backupAndStoreIfNeeded() throws IOException
    {
        if (currentFile.changed()) {
            if (backup) {
                final Path originalFile = currentFile.getFile();
                final Path backupFile = Paths.get(originalFile.toString() + backupSuffix);
                Files.move(originalFile, backupFile);
            }
            currentFile.store();
        }
    }

    @Override
    public void endFiles()
    {
        log.info( "Processed " + processedFileCount + (processedFileCount == 1 ? " file" : " files"));
        log.info( "Formatted " + editedFileCount + (editedFileCount == 1 ? " file" : " files") );
    }

    @Override
    public void handle( Violation violation )
    {
        violations.add( violation );
    }

    public boolean hasViolations()
    {
        return !violations.isEmpty();
    }

    @Override
    public void startFile(Resource file)
    {
        this.currentFile = (EditableDocument) file;
    }

    @Override
    public void startFiles()
    {
        processedFileCount = 0;
    }

}