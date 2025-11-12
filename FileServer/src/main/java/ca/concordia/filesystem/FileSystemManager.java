package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Arrays;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private FileSystemManager instance;
    private RandomAccessFile disk;
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if(instance == null) {
            //TODO Initialize the file system: >Progress below
             try {
                this.disk = new RandomAccessFile(filename, "rw");   // initialize 'virtual disk'
            } catch (IOException e) {
                throw new RuntimeException("Unable to open disk file: " + filename, e);
            }
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
            throw new IllegalArgumentException("Filename cannot be empty.");
        }
        int fileIndex = findFileIndex(fileName);
        if (fileIndex == -1){
            throw new IllegalArgumentException("File not found: " + fileName);
        }
        System.out.println(fileIndex);
        freeBlockList[fileIndex] = true; //frees block corresponding to file-to-be-deleted's first block
        inodeTable[fileIndex] = null;
    }

    public String listFiles(){
        String fileList = "";   //returns array of size = # of non-null entries
        for (int i = 0; i < MAXFILES; i++){
            if (inodeTable[i] != null){
                fileList += inodeTable[i].getFilename();
                fileList += ", ";
            }
        }
        if (fileList != ""){
            fileList = fileList.substring(0, fileList.length()-2);
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
            }
        }
        return -1;
    }

    public int countFiles(){
        int count = 0;
        for (int i = 0; i < MAXFILES; i++){
            if (inodeTable[i] != null) count++;
        }
        return count;
    }

    public void writeFile(String fileName, byte[] data) throws IOException{
        if(fileName == null || data == null) throw new IllegalArgumentException("File not found " + fileName);

        globalLock.lock();

        try {
            int fileIndex = findFileIndex(fileName);
            if(fileIndex == -1) throw new IllegalArgumentException("File not found" + fileName);
            
            int blocksNeeded = (data.length + BLOCK_SIZE - 1) /BLOCK_SIZE;

            int start = -1;

            //find a slot of freeblocks of length blocksNeeded
            outer:
            for(int i =0; i < freeBlockList.length - blocksNeeded; i++){
                for(int j = 0; j < blocksNeeded; j++){
                    if(!freeBlockList[i+j]) continue outer;
                }
                start = i;
                break;
            }

            if(start == -1) throw new IllegalStateException("Not enough free blocks.");
        
            // mark blocks allocated
            for (int i = 0; i < blocksNeeded; i++) {
                freeBlockList[start + i] = false;
            }

            // write data to disk at block offset
            disk.seek((long) start * BLOCK_SIZE);
            disk.write(data);

            // update inode (replace entry with same filename, size and start)
            FEntry updated = new FEntry(fileName, (short) data.length, (short) start);
            inodeTable[fileIndex] = updated;
        } finally {
            globalLock.unlock();
        }
        
        

    }

    public byte[] readFile(String fileName) throws IOException {
        if (fileName == null) throw new IllegalArgumentException("Invalid args");
        globalLock.lock();
        try {
            int fileIndex = findFileIndex(fileName);
            if (fileIndex == -1) throw new IllegalArgumentException("File not found: " + fileName);
            FEntry entry = inodeTable[fileIndex];
            if (entry == null) throw new IllegalArgumentException("File has no inode");
            int size = entry.getFilesize();
            short start = entry.getFirstBlock();
            if (start < 0 || size <= 0) return new byte[0];
            byte[] buf = new byte[size];
            disk.seek((long) start * BLOCK_SIZE);
            disk.readFully(buf);
            return buf;
        } finally {
            globalLock.unlock();
        }
    }


    // TODO: Add other required methods,
}
