package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private final static FileSystemManager instance;
    private final RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if(instance == null) {
            //TODO Initialize the file system: >Progress below
            this.disk = new RandomAccessFile(diskfile, "rw");   //initialize 'virtual disk' for managing files
            this.inodeTable = new FEntry[MAXFILES]; //initialize array of file entries
            this.freeBlockList = new boolean[MAXBLOCKS]; //initializes array of free block (all blocks start free)
            Arrays.fill(freeBlockList, true);

            instance = this;

        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void createFile(String fileName){
        int freeNodeIndex = findFreeInode();
        if (freeNodeIndex == -1){
            throw new IllegalStateException("No free filespace.");
        }
        if (findFileIndex(fileName) != -1){  //otherwise returns index of file if found with name fileName
            throw new IllegalArgumentException("File already exists: " + fileName);
        }
        FEntry newFile = new FEntry(fileName, (short) 0, (short) -1); //filesize 0, no blocks allocated (signalled by -1)
        this.inodeTable[freeNodeIndex] = newFile;
    }

    public void deleteFile(String fileName){
        if (fileName == null){
            throw new IllegalArgumentException("Filename cannot be empty.")
        };
        fileIndex = findFileIndex(fileName);
        if (fileIndex == -1){
            throw new IllegalArgumentException("File not found: " + fileName);
        };
        FEntry fileToDelete = inodeTable[fileIndex];
        freeBlockList[fileToDelete.getFirstBlock()] = true; //frees block corresponding to file-to-be-deleted's first block
        inodeTable[fileIndex] = null;
    }

    String[] listFiles(){
        String[] fileList = new String[countFiles()];   //returns array of size = # of non-null entries
        for (int i = 0; i < MAXFILES; i++){
            if (inodeTable[i] != null){
                fileList[i] = inodeTable[i].getFilename();
            };
        }
        return fileList;
    }

    private int findFreeInode(){
        for (int i = 0; i < MAXFILES; i++){
            if (inodeTable[i] == null) return i;
        }
        return -1;
    }

    private int findFileIndex(String fileName){
        for (int i = 0; i < MAXFILES; i++){
            if (inodeTable[i] != null && inodeTable[i].getFilename().equals(fileName)){
                return i;
            };
        }
        return -1;
    }

    private int countFiles(){
        count = 0;
        for (int i = 0; i < MAXFILES; i++){
            if (inodeTable[i] != null) count++;
        }
        return count;
    }



    // TODO: Add readFile, writeFile and other required methods,
}
