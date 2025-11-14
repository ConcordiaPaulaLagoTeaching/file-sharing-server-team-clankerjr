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
        if (instance == null) {
            try {
                this.disk = new RandomAccessFile(filename, "rw");
                this.inodeTable = new FEntry[MAXFILES];
                this.freeBlockList = new boolean[MAXBLOCKS];
                Arrays.fill(freeBlockList, true);

                if (disk.length() > 0) {
                    loadMetadata(); // Load metadata if the disk file already exists
                }

                instance = this;
            } catch (IOException e) {
                throw new RuntimeException("Unable to open disk file: " + filename, e);
            }
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }
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

    private void freeBlocks(FEntry entryToFree){
        int blocksUsed = (entryToFree.getFilesize() + BLOCK_SIZE - 1) / BLOCK_SIZE;
        int firstBlock = entryToFree.getFirstBlock();
        for (int i = firstBlock; i < firstBlock + blocksUsed; i++){
            freeBlockList[i] = true; //frees blocks corresponding to file-to-be-deleted's used blocks
        }
    }
    
    private void loadMetadata() {
        try {
            disk.seek(0); // Start of metadata region

            // Read inode table
            for (int i = 0; i < MAXFILES; i++) {
                int present = disk.readByte();
                if (present == 0) {
                    inodeTable[i] = null;
                    disk.skipBytes(15); // Skip unused metadata (11 bytes for name, 2 for size, 2 for block)
                } else {
                    byte[] nameBytes = new byte[11];
                    disk.read(nameBytes);
                    String fileName = new String(nameBytes).trim();
                    short fileSize = disk.readShort();
                    short firstBlock = disk.readShort();
                    inodeTable[i] = new FEntry(fileName, fileSize, firstBlock);
                }
            }

            // Read free block list
            for (int i = 0; i < MAXBLOCKS; i++) {
                freeBlockList[i] = disk.readByte() == 1;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load metadata", e);
        }
    }

    private void persistMetadata(){
        // Write inode table
        try{
            disk.seek(0); // start of metadata region
            for (int i = 0; i < MAXFILES; i++) {
                FEntry entry = inodeTable[i];
                if (entry == null) {
                    disk.writeByte(0); // not present
                    byte[] emptyName = new byte[11];
                    disk.write(emptyName);
                    disk.writeShort(0);
                    disk.writeShort(-1);
                } 
                else {
                    disk.writeByte(1); // present
                    byte[] nameBytes = new byte[11];
                    byte[] src = entry.getFilename().getBytes();
                    System.arraycopy(src, 0, nameBytes, 0, src.length);
                    disk.write(nameBytes);
                    disk.writeShort(entry.getFilesize());
                    disk.writeShort(entry.getFirstBlock());
                }
            }
                // Write free block list
            for (int i = 0; i < MAXBLOCKS; i++) {
                disk.writeByte(freeBlockList[i] ? 1 : 0);
            }
            // Force flush to disk
            disk.getChannel().force(true);
        } catch (IOException e){
            throw new RuntimeException("Failed to persist metadata", e);

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

        persistMetadata();
    }

    public void deleteFile(String fileName) throws IOException{
        if (fileName == null){
            throw new IllegalArgumentException("Filename cannot be empty.");
        }
        int fileIndex = findFileIndex(fileName);
        if (fileIndex == -1){
            throw new IllegalArgumentException("File not found: " + fileName);
        }
        FEntry entryToDelete = inodeTable[fileIndex];
        byte[] zeroOverwrite = new byte[entryToDelete.getFilesize()];
        disk.seek((long) (entryToDelete.getFirstBlock()+1) * BLOCK_SIZE);
        disk.write(zeroOverwrite);

        //clear file metadata
        freeBlocks(entryToDelete);
        //entryToDelete.setFilesize((short) 0);
        inodeTable[fileIndex] = null;
        persistMetadata();
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

    public void writeFile(String fileName, byte[] data) throws IOException{
        if(fileName == null || data == null) throw new IllegalArgumentException("File not found " + fileName);

        globalLock.lock();

        try {
            int fileIndex = findFileIndex(fileName);
            if(fileIndex == -1) throw new IllegalArgumentException("File not found" + fileName);
            
            int blocksNeeded = (data.length + BLOCK_SIZE - 1) /BLOCK_SIZE;

            int start = -1;
            //free blocks before writing new data
            FEntry entryToWrite = inodeTable[fileIndex];
            freeBlocks(entryToWrite);

            //find a slot of freeblocks of length blocksNeeded
            outer:
            for(int i =0; i <= freeBlockList.length - blocksNeeded; i++){
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
            disk.seek((long) (start+1) * BLOCK_SIZE);
            disk.write(data);

            // update inode (replace entry with same filename, size and start)
            FEntry updated = new FEntry(fileName, (short) data.length, (short) start);
            inodeTable[fileIndex] = updated;

            persistMetadata();
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
            disk.seek((long) (start+1) * BLOCK_SIZE);
            disk.readFully(buf);
            
            return buf;
        } finally {
            globalLock.unlock();
        }
    }
}
