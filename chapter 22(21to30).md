

# Page Address | T | P (Protection)

**Figure 22.5 Page-file page-table entry.** The valid bit is zero.

28 bits are used to index into a system data structure that indicates a file and  
offset within the file for the page, and the lower 3 bits specify the page state.

Invalid virtual addresses can also be in a number of temporary states that  
are part of the paging algorithms. When a page is removed from a process  
working set, it is moved either to the modified list (to be written to disk) or  
directly to the standby list. If written to the standby list, the page is reclaimed  
without being read from disk if it is needed again before it is moved to the free  
list. When possible, the VM manager uses idle CPU cycles to zero pages on the  
free list and move them to the zeroed list. Transition pages have been allocated  
a physical page and are awaiting the completion of the paging I/O before the  
PTE is marked as valid.

Windows XP uses section objects to describe pages that are sharable  
between processes. Each process has its own set of virtual page tables, but  
the section object also includes a set of page tables containing the master (or  
prototype) PTEs. When a PTE in a process page table is marked valid, it points  
to the physical page frame containing the page, as it must on IA32 processors,  
where the hardware MMU reads the page tables directly from memory. But  
when a shared page is made invalid, the PTE is edited to point to the prototype  
PTE associated with the section object.

The page tables associated with a section object are virtual insofar as they  
are created and trimmed as needed. The only prototype PTEs needed are  
those that describe pages for which there is a currently mapped view. This  
greatly improves performance and allows more efficient use of kernel virtual  
addresses.

The prototype PTE contains the page-frame address and the protection  
and state bits. Thus, the first access by a process to a shared page generates a  
page fault. After the first access, further accesses are performed in the normal  
manner. If a process writes to a copy-on-write page marked read-only in the  
PTE, the VM manager makes a copy of the page and marks the PTE writable,  
and the process effectively does not have a shared page any longer. Shared  
pages never appear in the page file but are instead found in the file system.

The VM manager keeps track of all pages of physical memory in a page-  
frame database. There is one entry for every page of physical memory in the  
system. The entry points to the PTE, which in turn points to the page frame, so  
the VM manager can maintain the state of the page. Page frames not referenced  
by a valid PTE are linked to lists according to page type, such as zeroed,  
modified, or free.

If a shared physical page is marked as valid for any process, the page  
cannot be removed from memory. The VM manager keeps a count of valid PTEs  
for each page in the page-frame database. When the count goes to zero, the  
physical page can be reused once its contents have been written back to disk  
(if it was marked dirty).

When a page fault occurs, the VM manager finds a physical page to hold  
the data. For zero-on-demand pages, the first choice is to find a page that has  
already been zeroed. If none is available, a page from the free list or standby  
list is chosen, and the page is zeroed before proceeding. If the faulted page  
has been marked as in transition, it is either already being read in from disk  
or has been unmapped or trimmed and is still available on the standby or  
modified list. The thread either waits for the I/O to complete or, in the latter  
cases, reclaims the page from the appropriate list.

Otherwise, an I/O must be issued to read the page in from the paging file  
or file system. The VM manager tries to allocate an available page from either  
the free list or the standby list. Pages in the modified list cannot be used until  
they have been written back to disk and transferred to the standby list. If no  
pages are available, the thread blocks until the working-set manager trims  
pages from memory or a page in physical memory is unmapped by a process.

Windows XP uses a per-process first-in, first-out (FIFO) replacement policy  
to take pages from processes that are using more than their minimum working-  
set size. Windows XP monitors the page faulting of each process that is at its  
minimum working-set size and adjusts the working-set size accordingly. When  
a process is started, it is assigned a default minimum working-set size of 50  
pages. The VM manager replaces and trims pages in the working set of a process  
according to their age. The age of a page is determined by how many trimming  
cycles have occurred without the PTE. Trimmed pages are moved to the standby  
or modified list, depending on whether the modified bit is set in the page's  
PTE.

The VM manager does not fault in only the page immediately needed.  
Research shows that the memory referencing of a thread tends to have a  
locality property; when a page is used, it is likely that adjacent pages will  
be referenced in the near future. (Think of iterating over an array or fetching  
sequential instructions that form the executable code for a thread.) Because of  
locality, when the VM manager faults in a page, it also faults in a few adjacent  
pages. This prefetching tends to reduce the total number of page faults. Writes  
are also clustered to reduce the number of independent I/O operations.

In addition to managing committed memory, the VM manager manages  
each process's reserved memory, or virtual address space. Each process has an  
associated splay tree that describes the ranges of virtual addresses in use and  
what the use is. This allows the VM manager to fault in page tables as needed.  
If the PTE for a faulting address does not exist, the VM manager searches for  
the address in the process's tree of virtual address descriptors (VADs) and  
uses this information to fill in the missing PTE and retrieve the page. In some  
cases, a page-table page itself may not exist; such a page must be transparently  
allocated and initialized by the VM manager.

## 22.3.3.3. Process Manager

The Windows XP process manager provides services for creating, deleting, and  
using processes, threads, and jobs. It has no knowledge about parent-child  
relationships or process hierarchies; those refinements are left to the particular  
environmental subsystem that owns the process. The process manager is also  
not involved in the scheduling of processes, other than setting the priorities and  
affinities in processes and threads when they are created. Thread scheduling  
takes place in the kernel dispatcher.

Each process contains one or more threads. Processes themselves can be  
collected together into large units called job objects; the use of job objects  
allows limits on CPU usage, working-set size, and processor affinities that  
control multiple processes at once. Job objects are used to manage large  
datacenter machines.

An example of process creation in the Win32 API environment is as follows.  
When a Win32 API application calls CreateProcess():

1. A message is sent to the Win32 API subsystem to notify it that the process  
is being created.

2. CreateProcess() in the original process then calls an API in the process  
manager of the NT executive to actually create the process.

3. The process manager calls the object manager to create a process object  
and returns the object handle to Win32 API.

4. Win32 API calls the process manager again to create a thread for the process  
and returns handles to the new process and thread.

The Windows XP APIs for manipulating virtual memory and threads and  
for duplicating handles take a process handle, so subsystems can perform  
operations on behalf of a new process without having to execute directly in  
the new process's context. Once a new process is created, the initial thread  
is created, and an asynchronous procedure call is delivered to the thread to  
prompt the start of execution at the user-mode image loader. The loader is in  
ntdll.dll, which is a link library automatically mapped into every newly created  
process. Windows XP also supports a UNIX fork() style of process creation in  
order to support the POSIX environmental subsystem. Although the Win32 API  
environment calls the process manager from the client process, POSIX uses the  
cross-process nature of the Windows XP APIs to create the new process from  
within the subsystem process.

The process manager also implements the queuing and delivery of asyn-  
chronous procedure calls (APCs) to threads. APCs are used by the system to  
initiate thread execution, complete I/O, terminate threads and processes, and  
attach debuggers. User-mode code can also queue an APC to a thread for  
delivery of signal-like notifications. To support POSIX, the process manager  
provides APIs that send alerts to threads to unblock them from system calls.

The debugger support in the process manager includes the capability to  
suspend and resume threads and to create threads that begin in a suspended  
mode. There are also process-manager APIs that get and set a thread's register  
context and access another process's virtual memory.

Threads can be created in the current process; they can also be injected into  
another process. Within the executive, existing threads can temporarily attach  
to another process. This method is used by worker threads that need to execute  
in the context of the process originating a work request.

The process manager also supports impersonation. A thread running in a  
process with a security token belonging to one user can set a thread-specific  
token belonging to another user. This facility is fundamental to the client-  
server computing model, where services need to act on behalf of a variety of  
clients with different security IDs.

## 22.3.3.4 Local Procedure Call Facility

The implementation of Windows XP uses a client-server model. The environ-  
mental subsystems are servers that implement particular operating-system  
personalities. The client-server model is used for implementing a variety  
of operating-system services besides the environmental subsystems. Security  
management, printer spooling, web services, network file systems, plug-and-  
play, and many other features are implemented using this model. To reduce  
the memory footprint, multiple services are often collected together into a few  
processes, which then rely on the user-mode thread-pool facilities to share  
threads and wait for messages (see Section 22.3.3.3).

The operating system uses the local procedure call (LPC) facility to pass  
requests and results between client and server processes within a single  
machine. In particular, LPC is used to request services from the various  
Windows XP subsystems. LPC is similar in many respects to the RPC mech-  
anisms used by many operating systems for distributed processing across  
networks, but LPC is optimized for use within a single system. The Windows  
XP implementation of Open Software Foundation (OSF) RPC often uses LPC as  
a transport on the local machine.

LPC is a message-passing mechanism. The server process publishes a  
globally visible connection-port object. When a client wants services from a  
subsystem, it opens a handle to the subsystem's connection-port object and  
sends a connection request to the port. The server creates a channel and returns  
a handle to the client. The channel consists of a pair of private communication  
ports: one for client-to-server messages and the other for server-to-client  
messages. Communication channels support a callback mechanism, so the  
client and server can accept requests when they would normally be expecting  
a reply.

When an LPC channel is created, one of three message-passing techniques  
must be specified.

1. The first technique is suitable for small messages (up to a couple  
of hundred bytes). In this case, the port's message queue is used as  
intermediate storage, and the messages are copied from one process to  
the other.

2. The second technique is for larger messages. In this case, a shared-  
memory section object is created for the channel. Messages sent through  
the port's message queue contain a pointer and size information referring  
to the section object. This avoids the need to copy large messages. The  
sender places data into the shared section, and the receiver views them  
directly.

3. The third technique uses the APIs that read and write directly into a  
process's address space. The LPC provides functions and synchronization  
so a server can access the data in a client.

**Figure 22.6 File I/O.**

The Win32 API window manager uses its own form of message passing  
that is independent of the executive LPC facilities. When a client asks for a  
connection that uses window-manager messaging, the server sets up three  
objects: (1) a dedicated server thread to handle requests, (2) a 64-KB section  
object, and (3) an event-pair object. An event-pair object is a synchronization  
object that is used by the Win32 API subsystem to provide notification when  
the client thread has copied a message to the Win32 API server, or vice versa.  
The section object passes the messages, and the event-pair object performs  
synchronization.

Window-manager messaging has several advantages:

- The section object eliminates message copying, since it represents a region  
of shared memory.

- The event-pair object eliminates the overhead of using the port object to  
pass messages containing pointers and lengths.

- The dedicated server thread eliminates the overhead of determining which  
client thread is calling the server, since there is one server thread per client  
thread.

- The kernel gives scheduling preference to these dedicated server threads  
to improve performance.

## 22.3.3.5 I/O Manager

The I/O manager is responsible for file systems, device drivers, and network  
drivers. It keeps track of which device drivers, filter drivers, and file systems  
are loaded, and it also manages buffers for I/O requests. It works with the  
VM manager to provide memory-mapped file I/O and controls the Windows  
XP cache manager, which handles caching for the entire I/O system. The I/O  
manager is fundamentally asynchronous. Synchronous I/O is provided by  
explicitly waiting for an I/O operation to complete. The I/O manager provides  
several models of asynchronous I/O completion, including setting of events,  
delivery of APCs to the initiating thread, and use of I/O completion ports, which  
allow a single thread to process I/O completions from many other threads.

Device drivers are arranged as a list for each device (called a driver or I/O  
stack because of how device drivers are added). The I/O manager converts the  
requests it receives into a standard form called an I/O request packet (IRP). It  
then forwards the IRP to the first driver in the stack for processing. After each  
driver processes the IRP, it calls the I/O manager either to forward it to the next  
driver in the stack or, if all processing is finished, to complete the operation on  
the IRP.

Completions may occur in a different context from the original I/O request.  
For example, if a driver is performing its part of an I/O operation and is forced  
to block for an extended time, it may queue the IRP to a worker thread to  
continue processing in the system context. In the original thread, the driver  
returns a status indicating that the I/O request is pending so that the thread  
can continue executing in parallel with the I/O operation. IRPs may also be  
processed in interrupt-service routines and completed in an arbitrary context.  
Because some final processing may need to happen in the context that initiated  
the I/O, the I/O manager uses an APC to do final I/O-completion processing in  
the context of the originating thread.

The stack model is very flexible. As a driver stack is built, various drivers  
have the opportunity to insert themselves into the stack as filter drivers.  
Filter drivers can examine and potentially modify each I/O operation. Mount  
management, partition management, and disk striping and mirroring are all  
examples of functionality implemented using filter drivers that execute beneath  
the file system in the stack. File-system filter drivers execute above the file  
system and have been used to implement functionality such as hierarchical  
storage management, single instancing of files for remote boot, and dynamic  
format conversion. Third parties also use file-system filter drivers to implement  
virus detection.

Device drivers for Windows XP are written to the Windows Driver Model  
(WDM) specification. This model lays out all the requirements for device drivers,  
including how to layer filter drivers, share common code for handling power  
and plug-and-play requests, build correct cancellation logic, and so forth.

Because of the richness of the WDM, writing a full WDM device driver  
for each new hardware device can involve an excessive amount of work.  
Fortunately, the port/miniport model makes it unnecessary to do this. Within  
a class of similar devices, such as audio drivers, SCSI devices, or Ethernet  
controllers, each instance of a device shares a common driver for that class,  
called a port driver. The port driver implements the standard operations for  
the class and then calls device-specific routines in the device's miniport driver  
to implement device-specific functionality.

## 22.3.3.6 Cache Manager

In many operating systems, caching is done by the file system. Instead,  
Windows XP provides a centralized caching facility. The cache manager works  
closely with the VM manager to provide cache services for all components  
under the control of the I/O manager. Caching in Windows XP is based on files  
rather than raw blocks.

The size of the cache changes dynamically according to how much free  
memory is available in the system. Recall that the upper 2 GB of a process's  
address space comprise the system area; it is available in the context of all  
processes. The VM manager allocates up to one-half of this space to the system  
cache. The cache manager maps files into this address space and uses the  
capabilities of the VM manager to handle file I/O.

The cache is divided into blocks of 256 KB. Each cache block can hold a  
view (that is, a memory-mapped region) of a file. Each cache block is described  
by a virtual address control block (VACB) that stores the virtual address and  
file offset for the view, as well as the number of processes using the view. The  
VACBs reside in a single array maintained by the cache manager.

For each open file, the cache manager maintains a separate VACB index  
array that describes the caching for the entire file. This array has an entry for  
each 256-KB chunk of the file; so, for instance, a 2-MB file would have an 8-entry  
VACB index array. An entry in the VACB index array points to the VACB if that  
portion of the file is in the cache; it is null otherwise. When the I/O manager  
receives a file's user-level read request, the I/O manager sends an IRP to the  
device-driver stack on which the file resides. The file system attempts to look  
up the requested data in the cache manager (unless the request specifically asks  
for a noncached read). The cache manager calculates which entry of that file's  
VACB index array corresponds to the byte offset of the request. The entry either  
points to the view in the cache or is invalid. If it is invalid, the cache manager  
allocates a cache block (and the corresponding entry in the VACB array) and  
maps the view into the cache block. The cache manager then attempts to copy  
data from the mapped file to the caller's buffer. If the copy succeeds, the  
operation is completed.

If the copy fails, it does so because of a page fault, which causes the VM  
manager to send a noncached read request to the I/O manager. The I/O manager  
sends another request down the driver stack, this time requesting a paging  
operation, which bypasses the cache manager and reads the data from the file  
directly into the page allocated for the cache manager. Upon completion, the  
VACB is set to point at the page. The data, now in the cache, are copied to the  
caller's buffer, and the original I/O request is completed. Figure 22.6 shows an  
overview of these operations.

When possible, for synchronous operations on cached files, I/O is handled  
by the fast I/O mechanism. This mechanism parallels the normal IRP-based  
I/O but calls into the driver stack directly rather than passing down an IRP.  
Because no IRP is involved, the operation should not block for an extended  
period of time and cannot be queued to a worker thread. Therefore, when the  
operation reaches the file system and calls the cache manager, the operation  
fails if the information is not already in cache. The I/O manager then attempts  
the operation using the normal IRP path.

A kernel-level read operation is similar, except that the data can be accessed  
directly from the cache, rather than being copied to a buffer in user space.  
To use file-system metadata (data structures that describe the file system),  
the kernel uses the cache manager's mapping interface to read the metadata.  
To modify the metadata, the file system uses the cache manager's pinning  
interface. Pinning a page locks the page into a physical-memory page frame  
so that the VM manager cannot move or page out the page. After updating  
the metadata, the file system asks the cache manager to unpin the page. A  
modified page is marked dirty, and so the VM manager flushes the page to  
disk. The metadata is stored in a regular file.

To improve performance, the cache manager keeps a small history of read  
requests and from this history attempts to predict future requests. If the cache  
manager finds a pattern in the previous three requests, such as sequential access  
forward or backward, it prefetches data into the cache before the next request is  
submitted by the application. In this way, the application finds its data already  
cached and does not need to wait for disk I/O. The Win32 API OpenFile() and  
CreateFile() functions can be passed the FILE_FLAG_SEQUENTIAL_SCAN flag,  
which is a hint to the cache manager to try to prefetch 192 KB ahead of the  
thread's requests. Typically, Windows XP performs I/O operations in chunks of  
64 KB or 16 pages; thus, this read-ahead is three times the normal amount.

The cache manager is also responsible for telling the VM manager to flush  
the contents of the cache. The cache manager's default behavior is write-back  
caching: It accumulates writes for 4 to 5 seconds and then wakes up the cache-  
writer thread. When write-through caching is needed, a process can set a flag  
when opening the file, or the process can call an explicit cache-flush function.

A fast-writing process could potentially fill all the free cache pages before  
the cache-writer thread had a chance to wake up and flush the pages to disk.  
The cache writer prevents a process from flooding the system in the following  
way. When the amount of free cache memory becomes low, the cache manager  
temporarily blocks processes attempting to write data and wakes the cache-  
writer thread to flush pages to disk. If the fast-writing process is actually a  
network redirector for a network file system, blocking it for too long could  
cause network transfers to time out and be retransmitted. This retransmission  
would waste network bandwidth. To prevent such waste, network redirectors  
can instruct the cache manager to limit the backlog of writes in the cache.

Because a network file system needs to move data between a disk and the  
network interface, the cache manager also provides a DMA interface to move  
the data directly. Moving data directly avoids the need to copy data through  
an intermediate buffer.

## 22.3.3.7 Security Reference Monitor

Centralizing management of system entities in the object manager enables  
Windows XP to use a uniform mechanism to perform run-time access validation  
and audit checks for every user-accessible entity in the system. Whenever a  
process opens a handle to an object, the security reference monitor (SRM)  
checks the process's security token and the object's access-control list to see  
whether the process has the necessary rights.

The SRM is also responsible for manipulating the privileges in security  
tokens. Special privileges are required for users to perform backup or restore  
operations on file systems, overcome certain checks as an administrator, debug  
processes, and so forth. Tokens can also be marked as being restricted in their  
privileges so that they cannot access objects that are available to most users.  
Restricted tokens are primarily used to restrict the damage that can be done by  
execution of untrusted code.

Another responsibility of the SRM is logging security audit events. A C-2  
security rating requires that the system have the ability to detect and log all  
attempts to access system resources so that it is easier to trace attempts at  
unauthorized access. Because the SRM is responsible for making access checks,  
it generates most of the audit records in the security-event log.

## 22.3.3.8 Plug-and-Play and Power Managers

The operating system uses the plug-and-play (PnP) manager to recognize  
and adapt to changes in the hardware configuration. For PnP to work, both  
the device and the driver must support the PnP standard. The PnP manager  
automatically recognizes installed devices and detects changes in devices as the  
system operates. The manager also keeps track of resources used by a device,  
as well as potential resources that could be used, and takes care of loading  
the appropriate drivers. This management of hardware resources — primarily  
interrupts and I/O memory ranges—has the goal of determining a hardware  
configuration in which all devices are able to operate.

For example, if device B can use interrupt 5 and device A can use 5 or 7,  
then the PnP manager will assign 5 to B and 7 to A. In previous versions, the  
user might have had to remove device A and reconfigure it to use interrupt 7  
before installing device B. The user thus had to study system resources before  
installing new hardware and had to determine which devices were using which  
hardware resources. The proliferation of PCMCIA cards, laptop docks, and USB,  
IEEE 1394, Infiniband, and other hot-pluggable devices also dictates the support  
of dynamically configurable resources.

The PnP manager handles dynamic reconfiguration as follows. First, it  
gets a list of devices from each bus driver (for example, PCI, USB). It loads  
the installed driver (or installs one, if necessary) and sends an add-device  
request to the appropriate driver for each device. The PnP manager figures out  
the optimal resource assignments and sends a start-device request to each  
driver, along with the resource assignment for the device. If a device needs to  
be reconfigured, the PnP manager sends a query-stop request, which asks the  
driver whether the device can be temporarily disabled. If the driver can disable  
the device, then all pending operations are completed, and new operations are  
prevented from starting. Next, the PnP manager sends a stop request; it can  
then reconfigure the device with another start-device request.

The PnP manager also supports other requests, such as query-remove.  
This request, which is used when the user is getting ready to eject a PCMCIA  
device, operates in a fashion similar to query-stop. The surprise-remove  
request is used when a device fails or, more likely, when a user removes a  
PCMCIA device without stopping it first. The remove request tells the driver to  
stop using the device and release all resources allocated to it.

Windows XP supports sophisticated power management. Although these  
facilities are useful for home systems to reduce power consumption, their  
primary application is for ease of use (quicker access) and extending the battery  
life of laptops. The system and individual devices can be moved to low-power  
mode (called standby or sleep mode) when not in use, so the battery is primarily  
directed at physical memory (RAM) data retention. The system can turn itself  
back on when packets are received from the network, a phone line to a modem  
rings, or a user opens a laptop or pushes a soft power button. Windows XP  
can also hibernate a system by storing physical memory contents to disk and  
completely shutting down the machine, then restoring the system at a later  
point before execution continues.

Further strategies for reducing power consumption are supported as well.  
Rather than allowing it to spin in a processor loop when the CPU is idle,  
Windows XP moves the system to a state requiring lower power consumption.  
If the CPU is underutilized, Windows XP reduces the CPU clock speed, which  
can save significant power.

## 22.3.3.9 Registry

Windows XP keeps much of its configuration information in an internal  
database called the registry. A registry database is called a hive. There are  
separate hives for system information, default user preferences, software  
installation, and security. Because the information in the system hive is  
required in order to boot the system, the registry manager is implemented  
as a component of the executive.

Every time the system successfully boots, it saves the system hive as last  
known good. If the user installs software, such as a device driver, that produces  
a system-hive configuration that will not boot, the user can usually boot using  
the last-known-good configuration.

Damage to the system hive from installing third-party applications and  
drivers is so common that Windows XP has a component called system restore  
that periodically saves the hives, as well as other software states like driver  
executables and configuration files, so that the system can be restored to  
a previously working state in cases where the system boots but no longer  
operates as expected.

## 22.3.3.10 Booting

The booting of a Windows XP PC begins when the hardware powers on and  
the BIOS begins executing from ROM. The BIOS identifies the system device  
to be booted and loads and executes the bootstrap loader from the front of  
the disk. This loader knows enough about the file-system format to load the  
NTLDR program from the root directory of the system device. NTLDR is used to  
determine which boot device contains the operating system. Next, the NTLDR  
loads in the HAL library, the kernel, and the system hive from the boot device.  
From the system hive, it determines what device drivers are needed to boot  
the system (the boot drivers) and loads them. Finally, NTLDR begins kernel  
execution.

The kernel initializes the system and creates two processes. The system  
process contains all the internal worker threads and never executes in user  
mode. The first user-mode process created is SMSS, which is similar to the  
INIT (initialization) process in UNIX. SMSS does further initialization of the  
system, including establishing the paging files and loading device drivers, and  
creates the WINLOGON and CSRSS processes. CSRSS is the Win32 API subsystem.  
WINLOGON brings up the rest of the system, including the LSASS security  
subsystem and the remaining services needed to run the system.

The system optimizes the boot process by pre-loading files from disk based  
on previous boots of the system. Disk access patterns at boot are also used to  
lay out system files on disk to reduce the number of I/O operations required.  
The processes required to start the system are reduced by grouping services  
into a smaller number of processes.Into one process. All of these approaches contribute to a dramatic reduction in system boot time. Of course, system boot time is less important than it once was because of the sleep and hibernation capabilities of Windows XP, which allow users to power down their computers and then quickly resume where they left off.

## Environmental Subsystems

Environmental subsystems are user-mode processes layered over the native Windows XP executive services to enable Windows XP to run programs developed for other operating systems, including 16-bit Windows, MS-DOS, and POSIX. Each environmental subsystem provides a single application environment.

Windows XP uses the Win32 API subsystem as the main operating environment, and thus this subsystem starts all processes. When an application is executed, the Win32 API subsystem calls the VM manager to load the application's executable code. The memory manager returns a status to Win32 indicating the type of executable. If it is not a native Win32 API executable, the Win32 API environment checks whether the appropriate environmental subsystem is running; if the subsystem is not running, it is started as a user-mode process. The subsystem then takes control over the application startup.

The environmental subsystems use the LPC facility to provide operating-system services to client processes. The Windows XP subsystem architecture keeps applications from mixing API routines from different environments. For instance, a Win32 API application cannot make a POSIX system call, because only one environmental subsystem can be associated with each process.

Since each subsystem is run as a separate user-mode process, a crash in one has no effect on other processes. The exception is Win32 API, which provides all keyboard, mouse, and graphical display capabilities. If it fails, the system is effectively disabled and requires a reboot.

The Win32 API environment categorizes applications as either graphical or character based, where a character-based application is one that thinks interactive output goes to a character-based (command) window. Win32 API transforms the output of a character-based application to a graphical representation in the command window. This transformation is easy: Whenever an output routine is called, the environmental subsystem calls a Win32 routine to display the text. Since the Win32 API environment performs this function for all character-based windows, it can transfer screen text between windows via the clipboard. This transformation works for MS-DOS applications, as well as for POSIX command-line applications.

### 22.4.1 MS-DOS Environment

The MS-DOS environment does not have the complexity of the other Windows XP environmental subsystems. It is provided by a Win32 API application called the virtual DOS machine (VDM). Since the VDM is a user-mode process, it is paged and dispatched like any other Windows XP application. The VDM has an instruction-execution unit to execute or emulate Intel 486 instructions. The VDM also provides routines to emulate the MS-DOS ROM BIOS and "int 21" software-interrupt services and has virtual device drivers for the screen, keyboard, and communication ports. The VDM is based on MS-DOS 5.0 source code; it allocates at least 620 KB of memory to the application.

The Windows XP command shell is a program that creates a window that looks like an MS-DOS environment. It can run both 16-bit and 32-bit executables. When an MS-DOS application is run, the command shell starts a VDM process to execute the program.

If Windows XP is running on an IA32-compatible processor, MS-DOS graphical applications run in full-screen mode, and character applications can run full screen or in a window. Not all MS-DOS applications run under the VDM. For example, some MS-DOS applications access the disk hardware directly, so they fail to run on Windows XP because disk access is restricted to protect the file system. In general, MS-DOS applications that directly access hardware will fail to operate under Windows XP.

Since MS-DOS is not a multitasking environment, some applications have been written in such a way as to "hog" the CPU. For instance, the use of busy loops can cause time delays or pauses in execution. The scheduler in the kernel dispatcher detects such delays and automatically throttles the CPU usage, but this may cause the offending application to operate incorrectly.

### 22.4.2 16-Bit Windows Environment

The Win16 execution environment is provided by a VDM that incorporates additional software called Windows on Windows (WOW32 for 16-bit applications); this software provides the Windows 3.1 kernel routines and stub routines for window-manager and graphical-device-interface (GDI) functions. The stub routines call the appropriate Win32 API subroutines—converting, or thunking, 16-bit addresses into 32-bit addresses. Applications that rely on the internal structure of the 16-bit window manager or GDI may not work, because the underlying Win32 API implementation is, of course, different from true 16-bit Windows.

WOW32 can multitask with other processes on Windows XP, but it resembles Windows 3.1 in many ways. Only one Win16 application can run at a time, all applications are single threaded and reside in the same address space, and all share the same input queue. These features imply that an application that stops receiving input will block all the other Win16 applications, just as in Windows 3.x, and one Win16 application can crash other Win16 applications by corrupting the address space. Multiple Win16 environments can coexist, however, by using the command `start /separate win16application` from the command line.

There are relatively few 16-bit applications that users need to continue to run on Windows XP, but some of them include common installation (setup) programs. Thus, the WOW32 environment continues to exist primarily because a number of 32-bit applications cannot be installed on Windows XP without it.

### 22.4.3 32-Bit Windows Environment on IA64

The native environment for Windows on IA64 uses 64-bit addresses and the native IA64 instruction set. To execute IA32 programs in this environment requires a thunking layer to translate 32-bit Win32 API calls into the corresponding 64-bit calls—just as 16-bit applications require translation on IA32 systems. Thus, 64-bit Windows supports the WOW64 environment. The implementations of 32-bit and 64-bit Windows are essentially identical, and the IA64 processor provides direct execution of IA32 instructions, so WOW64 achieves a higher level of compatibility than WOW32.

### 22.4.4 Win32 Environment

The main subsystem in Windows XP is the Win32 API. It runs Win32 API applications and manages all keyboard, mouse, and screen I/O. Since it is the controlling environment, it is designed to be extremely robust. Several features of the Win32 API contribute to this robustness. Unlike processes in the Win16 environment, each Win32 process has its own input queue. The window manager dispatches all input on the system to the appropriate process's input queue, so a failed process does not block input to other processes.

The Windows XP kernel also provides preemptive multitasking, which enables the user to terminate applications that have failed or are no longer needed. The Win32 API also validates all objects before using them, to prevent crashes that could otherwise occur if an application tried to use an invalid or wrong handle. The Win32 API subsystem verifies the type of the object to which a handle points before using the object. The reference counts kept by the object manager prevent objects from being deleted while they are still being used and prevent their use after they have been deleted.

To achieve a high level of compatibility with Windows 95/98 systems, Windows XP allows users to specify that individual applications be run using a shim layer, which modifies the Win32 API to better approximate the behavior expected by old applications. For example, some applications expect to see a particular version of the system and fail on new versions. Frequently, applications have latent bugs that become exposed due to changes in the implementation. For example, using memory after freeing it may cause corruption only if the order of memory reuse by the heap changes; or an application may make assumptions about which errors can be returned by a routine or about the number of valid bits in an address. Running an application with the Windows 95/98 shims enabled causes the system to provide behavior much closer to Windows 95/98—though with reduced performance and limited interoperability with other applications.

### 22.4.5 POSIX Subsystem

The POSIX subsystem is designed to run POSIX applications written to follow the POSIX standard, which is based on the UNIX model. POSIX applications can be started by the Win32 API subsystem or by another POSIX application. POSIX applications use the POSIX subsystem server PSXSS.EXE, the POSIX dynamic link library PSXDLL.DLL, and the POSIX console session manager POSIX.EXE. Although the POSIX standard does not specify printing, POSIX applications can use printers transparently via the Windows XP redirection mechanism. POSIX applications have access to any file system on the Windows XP system; the POSIX environment enforces UNIX-like permissions on directory trees.

Due to scheduling issues, the POSIX system in Windows XP does not ship with the system but is available separately for professional desktop systems and servers. It provides a much higher level of compatibility with UNIX applications than previous versions of NT. Of the commonly available UNIX applications, most compile and run without change with the latest version of Interix.

### 22.4.6 Logon and Security Subsystems

Before a user can access objects on Windows XP, that user must be authenticated by the logon service, WINLOGON. WINLOGON is responsible for responding to the secure attention sequence (Control-Alt-Delete). The secure attention sequence is a required mechanism for keeping an application from acting as a Trojan horse. Only WINLOGON can intercept this sequence in order to put up a logon screen, change passwords, and lock the workstation. To be authenticated, a user must have an account and provide the password for that account. Alternatively, a user logs on by using a smart card and personal identification number, subject to the security policies in effect for the domain.

The local security authority subsystem (LSASS) is the process that generates access tokens to represent users on the system. It calls an authentication package to perform authentication using information from the logon subsystem or network server. Typically, the authentication package simply looks up the account information in a local database and checks to see that the password is correct. The security subsystem then generates the access token for the user ID containing the appropriate privileges, quota limits, and group IDs. Whenever the user attempts to access an object in the system, such as by opening a handle to the object, the access token is passed to the security reference monitor, which checks privileges and quotas. The default authentication package for Windows XP domains is Kerberos. LSASS also has the responsibility for implementing security policy such as strong passwords, for authenticating users, and for performing encryption of data and keys.

## File System

Historically, MS-DOS systems have used the file-allocation table (FAT) file system. The 16-bit FAT file system has several shortcomings, including internal fragmentation, a size limitation of 2 GB, and a lack of access protection for files. The 32-bit FAT file system has solved the size and fragmentation problems, but its performance and features are still weak by comparison with modern file systems. The NTFS file system is much better. It was designed to include many features, including data recovery, security, fault tolerance, large files and file systems, multiple data streams, UNICODE names, sparse files, encryption, journaling, volume shadow copies, and file compression.

Windows XP uses NTFS as its basic file system, and we focus on it here. Windows XP continues to use FAT16, however, to read floppies and other removable media. And despite the advantages of NTFS, FAT32 continues to be important for interoperability of media with Windows 95/98 systems. Windows XP supports additional file-system types for the common formats used for CD and DVD media.

### 22.5.1 NTFS Internal Layout

The fundamental entity in NTFS is a volume. A volume is created by the Windows XP logical-disk-management utility and is based on a logical disk partition. A volume may occupy a portion of a disk, may occupy an entire disk, or may span several disks.

NTFS does not deal with individual sectors of a disk but instead uses clusters as the units of disk allocation. A cluster is a number of disk sectors that is a power of 2. The cluster size is configured when an NTFS file system is formatted. The default cluster size is the sector size for volumes up to 512 MB, 1 KB for volumes up to 1 GB, 2 KB for volumes up to 2 GB, and 4 KB for larger volumes. This cluster size is much smaller than that for the 16-bit FAT file system, and the small size reduces the amount of internal fragmentation. As an example, consider a 1.6-GB disk with 16,000 files. If you use a FAT-16 file system, 400 MB may be lost to internal fragmentation because the cluster size is 32 KB. Under NTFS, only 17 MB would be lost when storing the same files.

NTFS uses logical cluster numbers (LCNs) as disk addresses. It assigns them by numbering clusters from the beginning of the disk to the end. Using this scheme, the system can calculate a physical disk offset (in bytes) by multiplying the LCN by the cluster size.

A file in NTFS is not a simple byte stream as it is in MS-DOS or UNIX; rather, it is a structured object consisting of typed attributes. Each attribute of a file is an independent byte stream that can be created, deleted, read, and written. Some attribute types are standard for all files, including the file name (or names, if the file has aliases, such as an MS-DOS shortname), the creation time, and the security descriptor that specifies access control. User data is stored in data attributes.

Most traditional data files have an unnamed data attribute that contains all the file's data. However, additional data streams can be created with explicit names. For instance, in Macintosh files stored on a Windows XP server, the resource fork is a named data stream. The IProp interfaces of the Component Object Model (COM) use a named data stream to store properties on ordinary files, including thumbnails of images. In general, attributes may be added as necessary and are accessed using a file-name:attribute syntax. NTFS returns the size of the unnamed attribute only in response to file-query operations, such as when running the dir command.

Every file in NTFS is described by one or more records in an array stored in a special file called the master file table (MFT). The size of a record is determined when the file system is created; it ranges from 1 to 4 KB. Small attributes are stored in the MFT record itself and are called resident attributes. Large attributes, such as the unnamed bulk data, are called nonresident attributes and are stored in one or more contiguous extents on the disk; a pointer to each extent is stored in the MFT record. For a small file, even the data attribute may fit inside the MFT record. If a file has many attributes—or if it is highly fragmented, so that many pointers are needed to point to all the fragments—one record in the MFT might not be large enough. In this case, the file is described by a record called the base file record, which contains pointers to overflow records that hold the additional pointers and attributes.

Each file in an NTFS volume has a unique ID called a file reference. The file reference is a 64-bit quantity that consists of a 48-bit file number and a 16-bit sequence number. The file number is the record number (that is, the array slot) in the MFT that describes the file. The sequence number is incremented every time an MFT entry is reused. The sequence number enables NTFS to perform internal consistency checks, such as catching a stale reference to a deleted file after the MFT entry has been reused for a new file.

#### 22.5.1.1 NTFS B+ Tree

As in MS-DOS and UNIX, the NTFS namespace is organized as a hierarchy of directories. Each directory uses a data structure called a B+ tree to store an index of the file names in that directory. A B+ tree is used because it eliminates the cost of reorganizing the tree and has the property that the length of every path from the root of the tree to a leaf is the same. The index root of a directory contains the top level of the B+ tree. For a large directory, this top level contains pointers to disk extents that hold the remainder of the tree. Each entry in the directory contains the name and file reference of the file, as well as a copy of the update timestamp and file size taken from the file's resident attributes in the MFT. Copies of this information are stored in the directory, so a directory listing can be efficiently generated. Because all the file names, sizes, and update times are available from the directory itself, there is no need to gather these attributes from the MFT entries for each of the files.

#### 22.5.1.2 NTFS Metadata

The NTFS volume's metadata are all stored in files. The first file is the MFT. The second file, which is used during recovery if the MFT is damaged, contains a copy of the first 16 entries of the MFT. The next few files are also special in purpose. They include the log file, volume file, attribute-definition table, root directory, bitmap file, boot file, and bad-cluster file. We describe the role of each of these files below.

- The log file records all metadata updates to the file system.
- The volume file contains the name of the volume, the version of NTFS that formatted the volume, and a bit that tells whether the volume may have been corrupted and needs to be checked for consistency.
- The attribute-definition table indicates which attribute types are used in the volume and what operations can be performed on each of them.
- The root directory is the top-level directory in the file-system hierarchy.
- The bitmap file indicates which clusters on a volume are allocated to files and which are free.
- The boot file contains the startup code for Windows XP and must be located at a particular disk address so that it can be found easily by a simple ROM bootstrap loader. The boot file also contains the physical address of the MFT.
- The bad-cluster file keeps track of any bad areas on the volume; NTFS uses this record for error recovery.

### 22.5.2 Recovery

In many simple file systems, a power failure at the wrong time can damage the file-system data structures so severely that the entire volume is scrambled. Many versions of UNIX store redundant metadata on the disk, and they recover from crashes using the fsck program to check all the file-system data structures and restore them forcibly to a consistent state. Restoring them often involves deleting damaged files and freeing data clusters that had been written with user data but not properly recorded in the file system's metadata structures. This checking can be a slow process and can cause the loss of significant amounts of data.

NTFS takes a different approach to file-system robustness. In NTFS, all file-system data-structure updates are performed inside transactions. Before a data structure is altered, the transaction writes a log record that contains redo and undo information; after the data structure has been changed, the transaction writes a commit record to the log to signify that the transaction succeeded.

After a crash, the system can restore the file-system data structures to a consistent state by processing the log records, first redoing the operations for committed transactions and then undoing the operations for transactions that did not commit successfully before the crash. Periodically (usually every 5 seconds), a checkpoint record is written to the log. The system does not need log records prior to the checkpoint to recover from a crash. They can be discarded, so the log file does not grow without bounds. The first time after system startup that an NTFS volume is accessed, NTFS automatically performs file-system recovery.

This scheme does not guarantee that all the user-file contents are correct after a crash; it ensures only that the file-system data structures (the metadata files) are undamaged and reflect some consistent state that existed prior to the crash. It would be possible to extend the transaction scheme to cover user files, and Microsoft may do so in the future.

The log is stored in the third metadata file at the beginning of the volume. It is created with a fixed maximum size when the file system is formatted. It has two sections: the logging area, which is a circular queue of log records, and the restart area, which holds context information, such as the position in the logging area where NTFS should start reading during a recovery. In fact, the restart area holds two copies of its information, so recovery is still possible if one copy is damaged during the crash.

The logging functionality is provided by the Windows XP log-file service. In addition to writing the log records and performing recovery actions, the log-file service keeps track of the free space in the log file. If the free space gets too low, the log-file service queues pending transactions, and NTFS halts all new I/O operations. After the in-progress operations complete, NTFS calls the cache manager to flush all data, then resets the log file and performs the queued transactions.

### 22.5.3 Security

The security of an NTFS volume is derived from the Windows XP object model. Each NTFS file references a security descriptor, which contains the access token of the owner of the file, and an access-control list, which states the access privileges granted to each user having access to the file.

In normal operation, NTFS does not enforce permissions on traversal of directories in file path names. However, for compatibility with POSIX, these checks can be enabled. Traversal checks are inherently more expensive, since modern parsing of file path names uses prefix matching rather than component-by-component opening of directory names.

### 22.5.4 Volume Management and Fault Tolerance

FtDisk is the fault-tolerant disk driver for Windows XP. When installed, it provides several ways to combine multiple disk drives into one logical volume so as to improve performance, capacity, or reliability.

#### 22.5.4.1 Volume Set

One way to combine multiple disks is to concatenate them logically to form a large logical volume, as shown in Figure 22.7. In Windows XP, this logical volume, called a volume set, can consist of up to 32 physical partitions. A volume set that contains an NTFS volume can be extended without disturbance of the data already stored in the file system. The bitmap metadata on the NTFS volume are simply extended to cover the newly added space. NTFS continues to use the same LCN mechanism that it uses for a single physical disk, and the FtDisk driver supplies the mapping from a logical-volume offset to the offset on one particular disk.

#### 22.5.4.2 Stripe Set

Another way to combine multiple physical partitions is to interleave their blocks in round-robin fashion to form what is called a stripe set, as shown in Figure 22.8. This scheme is also called RAID level 0, or disk striping. FtDisk uses a stripe size of 64 KB: The first 64 KB of the logical volume are stored in the first physical partition, the second 64 KB in the second physical partition, and so on, until each partition has contributed 64 KB of space. Then, the allocation wraps around to the first disk, allocating the second 64-KB block. A stripe set forms one large logical volume, but the physical layout can improve the I/O bandwidth, because, for a large I/O, all the disks can transfer data in parallel.

#### 22.5.4.3 Stripe Set with Parity

A variation of this idea is the stripe set with parity, which is shown in Figure 22.9. This scheme is also called RAID level 5. Suppose that a stripe set has eight disks. Seven of the disks will store data stripes, with one data stripe on each disk, and the eighth disk will store a parity stripe for each data stripe. The parity stripe contains the byte-wise exclusive or of the data stripes. If any one of the eight stripes is destroyed, the system can reconstruct the data by calculating the exclusive or of the remaining seven. This ability to reconstruct data makes the disk array much less likely to lose data in case of a disk failure.

Notice that an update to one data stripe also requires recalculation of the parity stripe. Seven concurrent writes to seven different data stripes thus would also require updates to seven parity stripes. If the parity stripes were all on the same disk, that disk could have seven times the I/O load of the data disks. To avoid creating this bottleneck, we spread the parity stripes over all the disks by assigning them in round-robin style. To build a stripe set with parity, we need a minimum of three equal-sized partitions located on three separate disks.

#### 22.5.4.4 Disk Mirroring

An even more robust scheme is called disk mirroring or RAID level 1; it is depicted in Figure 22.10. A mirror set comprises two equal-sized partitions on two disks. When an application writes data to a mirror set, FtDisk writes the data to both partitions, so that the data contents of the two partitions are identical. If one partition fails, FtDisk has another copy safely stored on the mirror. Mirror sets can also improve performance, because read requests can be split between the two mirrors, giving each mirror half of the workload. To protect against the failure of a disk controller, we can attach the two disks of a mirror set to two separate disk controllers. This arrangement is called a duplex set.

#### 22.5.4.5 Sector Sparing and Cluster Remapping

To deal with disk sectors that go bad, FtDisk uses a hardware technique called sector sparing, and NTFS uses a software technique called cluster remapping. Sector sparing is a hardware capability provided by many disk drives. When a disk drive is formatted, it creates a map from logical block numbers to good sectors on the disk. It also leaves extra sectors unmapped, as spares. If a sector fails, FtDisk instructs the disk drive to substitute a spare. Cluster remapping is a software technique performed by the file system. If a disk block goes bad, NTFS substitutes a different, unallocated block by changing any affected pointers in the MFT. NTFS also makes a note that the bad block should never be allocated to any file.
When a disk block goes bad, the usual outcome is a data loss. But sector sparing or cluster remapping can be combined with fault-tolerant volumes to mask the failure of a disk block. If a read fails, the system reconstructs the missing data by reading the mirror or by calculating the exclusive or parity in a stripe set with parity. The reconstructed data are stored into a new location that is obtained by sector sparing or cluster remapping.

## 22.5.5 Compression and Encryption

NTFS can perform data compression on individual files or on all data files in a directory. To compress a file, NTFS divides the file's data into compression units, which are blocks of 16 contiguous clusters. When each compression unit is written, a data-compression algorithm is applied. If the result fits into fewer than 16 clusters, the compressed version is stored. When reading, NTFS can determine whether data have been compressed: If they have been, the length of the stored compression unit is less than 16 clusters. To improve performance when reading contiguous compression units, NTFS prefetches and decompresses ahead of the application requests.

For sparse files or files that contain mostly zeros, NTFS uses another technique to save space. Clusters that contain only zeros because they have never been written are not actually allocated or stored on disk. Instead, gaps are left in the sequence of virtual-cluster numbers stored in the MFT entry for the file. When reading a file, if it finds a gap in the virtual-cluster numbers, NTFS just zero-fills that portion of the caller's buffer. This technique is also used by UNIX.

NTFS supports encryption of files. Individual files or entire directories can be specified for encryption. The security system manages the keys used, and a key-recovery service is available to retrieve lost keys.

## 22.5.6 Mount Points

Mount points are a form of symbolic link specific to directories on NTFS. They provide a mechanism for administrators to organize disk volumes that is more flexible than the use of global names (like drive letters). Mount points are implemented as a symbolic link with associated data that contain the true volume name. Ultimately, mount points will supplant drive letters completely, but there will be a long transition due to the dependence of many applications on the drive-letter scheme.

## 22.5.7 Change Journal

NTFS keeps a journal describing all changes that have been made to the file system. User-mode services can receive notifications of changes to the journal and then identify what files have changed. The content-indexing service uses the change journal to identify files that need to be re-indexed. The file-replication service uses it to identify files that need to be replicated across the network.

## 22.5.8 Volume Shadow Copies

Windows XP implements the capability of bringing a volume to a known state and then creating a shadow copy that can be used to back up a consistent view of the volume. Making a shadow copy of a volume is a form of copy-on-write, where blocks modified after the shadow copy is created have their original contents stashed in the copy. To achieve a consistent state for the volume requires the cooperation of applications, since the system cannot know when the data used by the application are in a stable state from which the application could be safely restarted.

The server version of Windows XP uses shadow copies to efficiently maintain old versions of files stored on file servers. This allows users to see documents stored on file servers as they existed at earlier points in time. The user can use this feature to recover files that were accidentally deleted or simply to look at a previous version of the file, all without pulling out a backup tape.

# Networking

Windows XP supports both peer-to-peer and client-server networking. It also has facilities for network management. The networking components in Windows XP provide data transport, interprocess communication, file sharing across a network, and the ability to send print jobs to remote printers.

## 22.6.1 Network Interfaces

To describe networking in Windows XP, we must first mention two of the internal networking interfaces: the network device interface specification (NDIS) and the transport driver interface (TDI). The NDIS interface was developed in 1989 by Microsoft and 3Com to separate network adapters from transport protocols so that either could be changed without affecting the other. NDIS resides at the interface between the data-link-control and media-access-control layers in the OSI model and enables many protocols to operate over many different network adapters. In terms of the OSI model, the TDI is the interface between the transport layer (layer 4) and the session layer (layer 5). This interface enables any session-layer component to use any available transport mechanism. (Similar reasoning led to the streams mechanism in UNIX.) The TDI supports both connection-based and connectionless transport and has functions to send any type of data.

## 22.6.2 Protocols

Windows XP implements transport protocols as drivers. These drivers can be loaded and unloaded from the system dynamically, although in practice the system typically has to be rebooted after a change. Windows XP comes with several networking protocols. Next, we discuss a number of the protocols supported in Windows XP to provide a variety of network functionality.

### 22.6.2.1 Server-Message Block

The server-message-block (SMB) protocol was first introduced in MS-DOS 3.1. The system uses the protocol to send I/O requests over the network. The SMB protocol has four message types. The Session control messages are commands that start and end a redirector connection to a shared resource at the server. A redirector uses File messages to access files at the server. The system uses Printer messages to send data to a remote print queue and to receive back status information, and the Message message is used to communicate with another workstation. The SMB protocol was published as the Common Internet File System (CIFS) and is supported on a number of operating systems.

### 22.6.2.2 Network Basic Input/Output System

The network basic input/output system (NetBIOS) is a hardware-abstraction interface for networks, analogous to the BIOS hardware-abstraction interface devised for PCs running MS-DOS. NetBIOS, developed in the early 1980s, has become a standard network-programming interface. NetBIOS is used to establish logical names on the network, to establish logical connections, or sessions, between two logical names on the network, and to support reliable data transfer for a session via either NetBIOS or SMB requests.

### 22.6.2.3 NetBIOS Extended User Interface

The NetBIOS extended user interface (NetBEUI) was introduced by IBM in 1985 as a simple, efficient networking protocol for up to 254 machines. It is the default protocol for Windows 95 peer networking and for Windows for Workgroups. Windows XP uses NetBEUI when it wants to share resources with these networks. Among the limitations of NetBEUI are that it uses the actual name of a computer as the address and that it does not support routing.

### 22.6.2.4 Transmission Control Protocol/Internet Protocol

The transmission control protocol/Internet protocol (TCP/IP) suite that is used on the Internet has become the de facto standard networking infrastructure. Windows XP uses TCP/IP to connect to a wide variety of operating systems and hardware platforms. The Windows XP TCP/IP package includes the simple network-management protocol (SNMP), dynamic host-configuration protocol (DHCP), Windows Internet name service (WINS), and NetBIOS support.

### 22.6.2.5 Point-to-Point Tunneling Protocol

The point-to-point tunneling protocol (PPTP) is a protocol provided by Windows XP to communicate between remote-access server modules running on Windows XP server machines and other client systems that are connected over the Internet. The remote-access servers can encrypt data sent over the connection, and they support multi-protocol virtual private networks (VPNs) over the Internet.

### 22.6.2.6 Novell NetWare Protocols

The Novell NetWare protocols (IPX datagram service on the SPX transport layer) are widely used for PC LANs. The Windows XP NWLink protocol connects the NetBIOS to NetWare networks. In combination with a redirector (such as Microsoft's Client Service for NetWare or Novell's NetWare Client for Windows), this protocol enables a Windows XP client to connect to a NetWare server.

### 22.6.2.7 Web Distributed Authoring and Versioning Protocol

Web distributed authoring and versioning (WebDAV) is an HTTP-based protocol for collaborative authoring across the network. Windows XP builds a WebDAV redirector into the file system. By building WebDAV support directly into the file system, it can work with other features, such as encryption. Personal files can now be stored securely in a public place.

### 22.6.2.8 AppleTalk Protocol

The AppleTalk protocol was designed as a low-cost connection by Apple to allow Macintosh computers to share files. Windows XP systems can share files and printers with Macintosh computers via AppleTalk if a Windows XP server on the network is running the Windows Services for Macintosh package.

## 22.6.3 Distributed-Processing Mechanisms

Although Windows XP is not a distributed operating system, it does support distributed applications. Mechanisms that support distributed processing on Windows XP include NetBIOS, named pipes and mailslots, Windows sockets, RPCs, the Microsoft Interface Definition Language, and finally COM.

### 22.6.3.1 NetBIOS

In Windows XP, NetBIOS applications can communicate over the network using NetBEUI, NWLink, or TCP/IP.

### 22.6.3.2 Named Pipes

Named pipes are a connection-oriented messaging mechanism. Named pipes were originally developed as a high-level interface to NetBIOS connections over the network. A process can also use named pipes to communicate with other processes on the same machine. Since named pipes are accessed through the file-system interface, the security mechanisms used for file objects also apply to named pipes.

The name of a named pipe has a format called the uniform naming convention (UNC). A UNC name looks like a typical remote file name. The format of a UNC name is `\\server_name\share_name\x\y\z`, where the `server_name` identifies a server on the network; a `share_name` identifies any resource that is made available to network users, such as directories, files, named pipes, and printers; and the `\x\y\z` part is a normal file path name.

### 22.6.3.3 Mailslots

Mailslots are a connectionless messaging mechanism. They are unreliable when accessed across the network, in that a message sent to a mailslot may be lost before the intended recipient receives it. Mailslots are used for broadcast applications, such as finding components on the network; they are also used by the Windows computer browser service.

### 22.6.3.4 Winsock

Winsock is the Windows XP sockets API. Winsock is a session-layer interface that is largely compatible with UNIX sockets but has some added Windows XP extensions. It provides a standardized interface to many transport protocols that may have different addressing schemes, so that any Winsock application can run on any Winsock-compliant protocol stack.

### 22.6.3.5 Remote Procedure Calls

A remote procedure call (RPC) is a client-server mechanism that enables an application on one machine to make a procedure call to code on another machine. The client calls a local procedure—a stub routine—that packs its arguments into a message and sends them across the network to a particular server process. The client-side stub routine then blocks. Meanwhile, the server unpacks the message, calls the procedure, packs the return results into a message, and sends them back to the client stub. The client stub unblocks, receives the message, unpacks the results of the RPC, and returns them to the caller. This packing of arguments is sometimes called marshalling. The Windows XP RPC mechanism follows the widely used distributed-computing-environment standard for RPC messages, so programs written to use Windows XP RPCs are highly portable. The RPC standard is detailed. It hides many of the architectural differences among computers, such as the sizes of binary numbers and the order of bytes and bits in computer words, by specifying standard data formats for RPC messages.

Windows XP can send RPC messages using NetBIOS, or Winsock on TCP/IP networks, or named pipes on LAN Manager networks. The LPC facility, discussed earlier, is similar to RPC, except that in the case of LPC the messages are passed between two processes running on the same computer.

### 22.6.3.6 Microsoft Interface Definition Language

It is tedious and error-prone to write the code to marshal and transmit arguments in the standard format, to unmarshal and execute the remote procedure, to marshal and send the return results, and to unmarshal and return them to the caller. Fortunately, however, much of this code can be generated automatically from a simple description of the arguments and return results.

Windows XP provides the Microsoft Interface Definition Language to describe the remote procedure names, arguments, and results. The compiler for this language generates header files that declare the stubs for the remote procedures, as well as the data types for the argument and return-value messages. It also generates source code for the stub routines used at the client side and for an unmarshaller and dispatcher at the server side. When the application is linked, the stub routines are included. When the application executes the RPC stub, the generated code handles the rest.

### 22.6.3.7 Component Object Model

The component object model (COM) is a mechanism for interprocess communication that was developed for Windows. COM objects provide a well-defined interface to manipulate the data in the object. For instance, COM is the infrastructure used by Microsoft's object linking and embedding (OLE) technology for inserting spreadsheets into Microsoft Word documents. Windows XP has a distributed extension called DCOM that can be used over a network utilizing RPC to provide a transparent method of developing distributed applications.

## 22.6.4 Redirectors and Servers

In Windows XP, an application can use the Windows XP I/O API to access files from a remote computer as though they were local, provided that the remote computer is running a CIFS server, such as is provided by Windows XP or earlier Windows systems. A redirector is the client-side object that forwards I/O requests to remote files, where they are satisfied by a server. For performance and security, the redirectors and servers run in kernel mode.

In more detail, access to a remote file occurs as follows:

1. The application calls the I/O manager to request that a file be opened with a file name in the standard UNC format.
2. The I/O manager builds an I/O request packet, as described in Section 22.3.3.5.
3. The I/O manager recognizes that the access is for a remote file and calls a driver called a multiple universal-naming-convention provider (MUP).
4. The MUP sends the I/O request packet asynchronously to all registered redirectors.
5. A redirector that can satisfy the request responds to the MUP. To avoid asking all the redirectors the same question in the future, the MUP uses a cache to remember which redirector can handle this file.
6. The redirector sends the network request to the remote system.
7. The remote-system network drivers receive the request and pass it to the server driver.
8. The server driver hands the request to the proper local file-system driver.
9. The proper device driver is called to access the data.
10. The results are returned to the server driver, which sends the data back to the requesting redirector. The redirector then returns the data to the calling application via the I/O manager.

A similar process occurs for applications that use the Win32 API network API, rather than the UNC services, except that a module called a multi-provider router is used instead of a MUP.

For portability, redirectors and servers use the TDI API for network transport. The requests themselves are expressed in a higher-level protocol, which by default is the SMB protocol mentioned in Section 22.6.2. The list of redirectors is maintained in the system registry database.

### 22.6.4.1 Distributed File System

The UNC names are not always convenient, because multiple file servers may be available to serve the same content, and UNC names explicitly include the name of the server. Windows XP supports a distributed file system (DFS) protocol that allows a network administrator to serve up files from multiple servers using a single distributed name space.

### 22.6.4.2 Folder Redirection and Client-Side Caching

To improve the PC experience for business users who frequently switch among computers, Windows XP allows administrators to give users roaming profiles, which keep users' preferences and other settings on servers. Folder redirection is then used to automatically store a user's documents and other files on a server. This works well until one of the computers is no longer attached to the network, such as a laptop on an airplane. To give users off-line access to their redirected files, Windows XP uses client-side caching (CSC). CSC is used when the computer is online to keep copies of the server files on the local machine for better performance. The files are pushed up to the server as they are changed. If the computer becomes disconnected, the files are still available, and the update of the server is deferred until the next time the computer is online with a suitably performing network link.

## 22.6.5 Domains

Many networked environments have natural groups of users, such as students in a computer laboratory at school or employees in one department in a business. Frequently, we want all the members of the group to be able to access shared resources on their various computers in the group. To manage the global access rights within such groups, Windows XP uses the concept of a domain. Previously, these domains had no relationship whatsoever to the domain-name system (DNS) that maps Internet host names to IP addresses. Now, however, they are closely related.

Specifically, a Windows XP domain is a group of Windows XP workstations and servers that share a common security policy and user database. Since Windows XP now uses the Kerberos protocol for trust and authentication, a Windows XP domain is the same thing as a Kerberos realm. Previous versions of NT used the idea of primary and backup domain controllers; now all servers in a domain are domain controllers. In addition, previous versions required the setup of one-way trusts between domains. Windows XP uses a hierarchical approach based on DNS and allows transitive trusts that can flow up and down the hierarchy. This approach reduces the number of trusts required for n domains from n * (n - 1) to O(n). The workstations in the domain trust the domain controller to give correct information about the access rights of each user (via the user's access token). All users retain the ability to restrict access to their own workstations, no matter what any domain controller may say.

### 22.6.5.1 Domain Trees and Forests

Because a business may have many departments and a school may have many classes, it is often necessary to manage multiple domains within a single organization. A domain tree is a contiguous DNS naming hierarchy for managing multiple domains. For example, bell-labs.com might be the root of the tree, with research.bell-labs.com and pez.bell-labs.com as children—domains research and pez. A forest is a set of noncontiguous names. An example would be the trees bell-labs.com and lucent.com. A forest may be made up of only one domain tree, however.

### 22.6.5.2 Trust Relationships

Trust relationships may be set up between domains in three ways: one-way, transitive, and cross-link. Versions of NT through 4.0 allowed only one-way trusts. A one-way trust is exactly what its name implies: Domain A is told it can trust domain B. However, B will not trust A unless another relationship is configured. Under a transitive trust, if A trusts B and B trusts C, then A, B, and C all trust one another, since transitive trusts are two-way by default. Transitive trusts are enabled by default for new domains in a tree and can be configured only among domains within a forest. The third type, a cross-link trust, is useful to cut down on authentication traffic. Suppose that domains A and B are leaf nodes and that users in A often use resources in B. If a standard transitive trust is used, authentication requests must traverse up to the common ancestor of the two leaf nodes; but if A and B have a cross-linking trust established, the authentications are sent directly to the other node.

## 22.6.6 Active Directory

Active Directory is the Windows XP implementation of lightweight directory-access protocol (LDAP) services. Active Directory stores the topology information about the domain, keeps the domain-based user and group accounts and passwords, and provides a domain-based store for technologies like group policies and intellimirror.

Administrators use group policies to establish standards for desktop preferences and software. For many corporate information-technology groups, uniformity drastically reduces the cost of computing. Intellimirror is used in conjunction with group policies to specify what software should be available to each class of user, even automatically installing it on demand from a corporate server.

## 22.6.7 Name Resolution in TCP/IP Networks

On an IP network, name resolution is the process of converting a computer name to an IP address, such as resolving www.bell-labs.com to 135.104.1.14. Windows XP provides several methods of name resolution, including Windows Internet name service (WINS), broadcast-name resolution, domain-name system (DNS), a hosts file, and an LMHOSTS file. Most of these methods are used by many operating systems, so we describe only WINS here.

Under WINS, two or more WINS servers maintain a dynamic database of name-to-IP address bindings, along with client software to query the servers. At least two servers are used, so that the WINS service can survive a server failure and so that the name-resolution workload can be spread over multiple machines.

WINS uses the dynamic host-configuration protocol (DHCP). DHCP updates address configurations automatically in the WINS database, without user or administrator intervention, as follows. When a DHCP client starts up, it broadcasts a discover message. Each DHCP server that receives the message replies with an offer message that contains an IP address and configuration information for the client. The client chooses one of the configurations and sends a request message to the selected DHCP server. The DHCP server responds with the IP address and configuration information it gave previously and with a lease for that address. The lease gives the client the right to use the IP address for a specified period of time. When the lease time is half expired, the client attempts to renew the lease for the address. If the lease is not renewed, the client must obtain a new one.

# Programmer interface

The Win32 API is the fundamental interface to the capabilities of Windows XP. This section describes five main aspects of the Win32 API: access to kernel objects, sharing of objects between processes, process management, interprocess communication, and memory management.

## 22.7.1 Access to Kernel Objects

The Windows XP kernel provides many services that application programs can use. Application programs obtain these services by manipulating kernel objects. A process gains access to a kernel object named XXX by calling the CreateXXX function to open a handle to XXX. This handle is unique to the process. Depending on which object is being opened, if the Create() function fails, it may return 0, or it may return a special constant named INVALID_HANDLE_VALUE. A process can close any handle by calling the CloseHandle() function, and the system may delete the object if the count of processes using the object drops to 0.

## 22.7.2 Sharing Objects Between Processes

Windows XP provides three ways to share objects between processes. The first way is for a child process to inherit a handle to the object. When the parent calls the CreateXXX function, the parent supplies a SECURITY_ATTRIBUTES structure with the bInheritHandle field set to TRUE. This field creates an inheritable handle. Next, the child process is created, passing a value of TRUE to the CreateProcess() function's bInheritHandle argument. Figure 22.11 shows a code sample that creates a semaphore handle inherited by a child process.

Assuming the child process knows which handles are shared, the parent and child can achieve interprocess communication through the shared objects. In the example in Figure 22.11, the child process gets the value of the handle from the first command-line argument and then shares the semaphore with the parent process.

The second way to share objects is for one process to give the object a name when the object is created and for the second process to open the name. This method has two drawbacks: Windows XP does not provide a way to check whether an object with the chosen name already exists, and the object name space is global, without regard to the object type. For instance, two applications may create an object named pipe when two distinct and possibly different objects are desired.

Named objects have the advantage that unrelated processes can readily share them. The first process calls one of the CreateXXX functions and supplies a name in the lpszName parameter. The second process gets a handle to share the object by calling OpenXXX() (or CreateXXX) with the same name, as shown in the example of Figure 22.12.

The third way to share objects is via the DuplicateHandle() function. This method requires some other method of interprocess communication to pass the duplicated handle. Given a handle to a process and the value of a handle within that process, a second process can get a handle to the same object and thus share it. An example of this method is shown in Figure 22.13.

## 22.7.3 Process Management

In Windows XP, a process is an executing instance of an application, and a thread is a unit of code that can be scheduled by the operating system. Thus, a process contains one or more threads. A process is started when some other process calls the CreateProcess() routine. This routine loads any dynamic link libraries used by the process and creates a primary thread. Additional threads can be created by the CreateThread() function. Each thread is created with its own stack, which defaults to 1 MB unless specified otherwise in an argument to CreateThread(). Because some C run-time functions maintain state in static variables, such as errno, a multithread application needs to guard against unsynchronized access. The wrapper function beginthreadex() provides appropriate synchronization.

```c
SECURITY_ATTRIBUTES sa;
sa.nLength = sizeof(sa);
sa.lpSecurityDescriptor = NULL;
sa.bInheritHandle = TRUE;

HANDLE a_semaphore = CreateSemaphore(&sa, 1, 1, NULL);

char command_line[132];
ostrstream ostring(command_line, sizeof(command_line));
ostring << a_semaphore << ends;

CreateProcess("another_process.exe", command_line, NULL, NULL, TRUE, 0, NULL, NULL, NULL, NULL);
```

*Figure 22.11 Code enabling a child to share an object by inheriting a handle.*

```c
// Process A
HANDLE a_semaphore = CreateSemaphore(NULL, 1, 1, "MySEM1");

// Process B
HANDLE b_semaphore = OpenSemaphore(SEMAPHORE_ALL_ACCESS, FALSE, "MySEM1");
```

*Figure 22.12 Code for sharing an object by name lookup.*
```
Here's the continuation of the Markdown conversion:

```markdown
```c
// Process A wants to give Process B access to a semaphore

// Process A
HANDLE a_semaphore = CreateSemaphore(NULL, 1, 1, NULL);
// send the value of the semaphore to Process B
// using a message or shared memory object

// Process B
HANDLE process_a = OpenProcess(PROCESS_ALL_ACCESS, FALSE, process_id_of_A);
HANDLE b_semaphore;
DuplicateHandle(process_a, a_semaphore, GetCurrentProcess(), &b_semaphore, 0, FALSE, DUPLICATE_SAME_ACCESS);

// use b_semaphore to access the semaphore
```

*Figure 22.13 Code for sharing an object by passing a handle.*

## 22.7.3.1 Instance Handles

Every dynamic link library or executable file loaded into the address space of a process is identified by an instance handle. The value of the instance handle is actually the virtual address where the file is loaded. An application can get the handle to a module in its address space by passing the name of the module to `GetModuleHandle()`. If NULL is passed as the name, the base address of the process is returned. The lowest 64 KB of the address space are not used, so a faulty program that tries to de-reference a NULL pointer gets an access violation.

Priorities in the Win32 API environment are based on the Windows XP scheduling model, but not all priority values may be chosen. Win32 API uses four priority classes:

- `IDLE_PRIORITY_CLASS` (priority level 4)
- `NORMAL_PRIORITY_CLASS` (priority level 8)
- `HIGH_PRIORITY_CLASS` (priority level 13)
- `REALTIME_PRIORITY_CLASS` (priority level 24)

Processes are typically members of the `NORMAL_PRIORITY_CLASS` unless the parent of the process was of the `IDLE_PRIORITY_CLASS` or another class was specified when `CreateProcess` was called. The priority class of a process can be changed with the `SetPriorityClass()` function or by passing of an argument to the START command. For example, the command `START /REALTIME cbserver.exe` would run the cbserver program in the `REALTIME_PRIORITY_CLASS`. Only users with the increase scheduling priority privilege can move a process into the `REALTIME_PRIORITY_CLASS`. Administrators and power users have this privilege by default.

## 22.7.3.2 Scheduling Rule

When a user is running an interactive program, the system needs to provide especially good performance for the process. For this reason, Windows XP has a special scheduling rule for processes in the `NORMAL_PRIORITY_CLASS`. Windows XP distinguishes between the foreground process that is currently selected on the screen and the background processes that are not currently selected. When a process moves into the foreground, Windows XP increases the scheduling quantum by some factor—typically by 3. (This factor can be changed via the performance option in the system section of the control panel.) This increase gives the foreground process three times longer to run before a time-sharing preemption occurs.

## 22.7.3.3 Thread Priorities

A thread starts with an initial priority determined by its class. The priority can be altered by the `SetThreadPriority()` function. This function takes an argument that specifies a priority relative to the base priority of its class:

- `THREAD_PRIORITY_LOWEST`: base - 2
- `THREAD_PRIORITY_BELOW_NORMAL`: base - 1
- `THREAD_PRIORITY_NORMAL`: base + 0
- `THREAD_PRIORITY_ABOVE_NORMAL`: base + 1
- `THREAD_PRIORITY_HIGHEST`: base + 2

Two other designations are also used to adjust the priority. Recall from Section 22.3.2.1 that the kernel has two priority classes: 16-31 for the real-time class and 0-15 for the variable-priority class. `THREAD_PRIORITY_IDLE` sets the priority to 16 for real-time threads and to 1 for variable-priority threads. `THREAD_PRIORITY_TIME_CRITICAL` sets the priority to 31 for real-time threads and to 15 for variable-priority threads.

As we discussed in Section 22.3.2.1, the kernel adjusts the priority of a thread dynamically depending on whether the thread is I/O bound or CPU bound. The Win32 API provides a method to disable this adjustment via `SetProcessPriorityBoost()` and `SetThreadPriorityBoost()` functions.

## 22.7.3.4 Thread Synchronization

A thread can be created in a suspended state; the thread does not execute until another thread makes it eligible via the `ResumeThread()` function. The `SuspendThread()` function does the opposite. These functions set a counter, so if a thread is suspended twice, it must be resumed twice before it can run. To synchronize the concurrent access to shared objects by threads, the kernel provides synchronization objects, such as semaphores and mutexes.

In addition, synchronization of threads can be achieved by use of the `WaitForSingleObject()` and `WaitForMultipleObjects()` functions. Another method of synchronization in the Win32 API is the critical section. A critical section is a synchronized region of code that can be executed by only one thread at a time. A thread establishes a critical section by calling `InitializeCriticalSection()`. The application must call `EnterCriticalSection()` before entering the critical section and `LeaveCriticalSection()` after exiting the critical section. These two routines guarantee that, if multiple threads attempt to enter the critical section concurrently, only one thread at a time will be permitted to proceed; the others will wait in the `EnterCriticalSection()` routine. The critical-section mechanism is faster than using kernel-synchronization objects because it does not allocate kernel objects until it first encounters contention for the critical section.

## 22.7.3.5 Fibers

A fiber is user-mode code that is scheduled according to a user-defined scheduling algorithm. A process may have multiple fibers in it, just as it may have multiple threads. A major difference between threads and fibers is that whereas threads can execute concurrently, only one fiber at a time is permitted to execute, even on multiprocessor hardware. This mechanism is included in Windows XP to facilitate the porting of those legacy UNIX applications that were written for a fiber-execution model.

The system creates a fiber by calling either `ConvertThreadToFiber()` or `CreateFiber()`. The primary difference between these functions is that `CreateFiber()` does not begin executing the fiber that was created. To begin execution, the application must call `SwitchToFiber()`. The application can terminate a fiber by calling `DeleteFiber()`.

## 22.7.3.6 Thread Pool

Repeated creation and deletion of threads can be expensive for applications and services that perform small amounts of work in each. The thread pool provides user-mode programs with three services: a queue to which work requests may be submitted (via the `QueueUserWorkItem()` API), an API that can be used to bind callbacks to waitable handles (`RegisterWaitForSingleObject()`), and APIs to bind callbacks to timeouts (`CreateTimerQueue()` and `CreateTimerQueueTimer()`).

The thread pool's goal is to increase performance. Threads are relatively expensive, and a processor can only be executing one thing at a time no matter how many threads are used. The thread pool attempts to reduce the number of outstanding threads by slightly delaying work requests (reusing each thread for many requests) while providing enough threads to effectively utilize the machine's CPUs. The wait and timer-callback APIs allow the thread pool to further reduce the number of threads in a process, using far fewer threads than would be necessary if a process were to devote one thread to servicing each waitable handle or timeout.

## 22.7.4 Interprocess Communication

Win32 API applications handle interprocess communication in several ways. One way is by sharing kernel objects. Another way is by passing messages, an approach that is particularly popular for Windows GUI applications. One thread can send a message to another thread or to a window by calling `PostMessage()`, `PostThreadMessage()`, `SendMessage()`, `SendThreadMessage()`, or `SendMessageCallback()`. The difference between posting a message and sending a message is that the post routines are asynchronous: They return immediately, and the calling thread does not know when the message is actually delivered. The send routines are synchronous: They block the caller until the message has been delivered and processed.

In addition to sending a message, a thread can send data with the message. Since processes have separate address spaces, the data must be copied. The system copies data by calling `SendMessage()` to send a message of type `WM_COPYDATA` with a `COPYDATASTRUCT` data structure that contains the length and address of the data to be transferred. When the message is sent, Windows XP copies the data to a new block of memory and gives the virtual address of the new block to the receiving process.

Unlike threads in the 16-bit Windows environment, every Win32 API thread has its own input queue from which it receives messages. (All input is received via messages.) This structure is more reliable than the shared input queue of 16-bit Windows, because, with separate queues, it is no longer possible for one stuck application to block input to the other applications. If a Win32 API application does not call `GetMessage()` to handle events on its input queue, the queue fills up; and after about five seconds, the system marks the application as "Not Responding".

## 22.7.5 Memory Management

The Win32 API provides several ways for an application to use memory: virtual memory, memory-mapped files, heaps, and thread-local storage.

### 22.7.5.1 Virtual Memory

An application calls `VirtualAlloc()` to reserve or commit virtual memory and `VirtualFree()` to decommit or release the memory. These functions enable the application to specify the virtual address at which the memory is allocated. They operate on multiples of the memory page size, and the starting address of an allocated region must be greater than 0x10000. Examples of these functions appear in Figure 22.14.

A process may lock some of its committed pages into physical memory by calling `VirtualLock()`. The maximum number of pages a process can lock is 30, unless the process first calls `SetProcessWorkingSetSize()` to increase the maximum working-set size.

```c
// allocate 16 MB at the top of our address space
void *buf = VirtualAlloc(0, 0x1000000, MEM_RESERVE | MEM_TOP_DOWN, PAGE_READWRITE);

// commit the upper 8 MB of the allocated space
VirtualAlloc(buf + 0x800000, 0x800000, MEM_COMMIT, PAGE_READWRITE);

// do something with the memory

// now decommit the memory
VirtualFree(buf + 0x800000, 0x800000, MEM_DECOMMIT);

// release all of the allocated address space
VirtualFree(buf, 0, MEM_RELEASE);
```

*Figure 22.14 Code fragments for allocating virtual memory.*

### 22.7.5.2 Memory-Mapping Files

Another way for an application to use memory is by memory-mapping a file into its address space. Memory mapping is also a convenient way for two processes to share memory: Both processes map the same file into their virtual memory. Memory mapping is a multistage process, as you can see in the example in Figure 22.15.

If a process wants to map some address space just to share a memory region with another process, no file is needed. The process calls `CreateFileMapping()` with a file handle of 0xFFFFFFFF and a particular size. The resulting file-mapping object can be shared by inheritance, by name lookup, or by duplication.

```c
// open the file or create it if it does not exist
HANDLE hfile = CreateFile("somefile", GENERIC_READ | GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL, OPEN_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);

// create the file mapping 8 MB in size
HANDLE hmap = CreateFileMapping(hfile, PAGE_READWRITE, SEC_COMMIT, 0, 0x800000, "SHM_1");

// now get a view of the space mapped
void *buf = MapViewOfFile(hmap, FILE_MAP_ALL_ACCESS, 0, 0, 0, 0x800000);

// do something with the mapped file

// now unmap the file
UnmapViewOfFile(buf);
CloseHandle(hmap);
CloseHandle(hfile);
```

*Figure 22.15 Code fragments for memory mapping of a file.*

### 22.7.5.3 Heaps

Heaps provide a third way for applications to use memory. A heap in the Win32 environment is a region of reserved address space. When a Win32 API process is initialized, it is created with a 1-MB default heap. Since many Win32 API functions use the default heap, access to the heap is synchronized to protect the heap's space-allocation data structures from being damaged by concurrent updates by multiple threads.

Win32 API provides several heap-management functions so that a process can allocate and manage a private heap. These functions are `HeapCreate()`, `HeapAlloc()`, `HeapRealloc()`, `HeapSize()`, `HeapFree()`, and `HeapDestroy()`. The Win32 API also provides the `HeapLock()` and `HeapUnlock()` functions to enable a thread to gain exclusive access to a heap. Unlike `VirtualLock()`, these functions perform only synchronization; they do not lock pages into physical memory.

### 22.7.5.4 Thread-Local Storage

The fourth way for applications to use memory is through a thread-local storage mechanism. Functions that rely on global or static data typically fail to work properly in a multithreaded environment. For instance, the C run-time function `strtok()` uses a static variable to keep track of its current position while parsing a string. For two concurrent threads to execute `strtok()` correctly, they need separate current position variables. The thread-local storage mechanism allocates global storage on a per-thread basis. It provides both dynamic and static methods of creating thread-local storage. The dynamic method is illustrated in Figure 22.16.

To use a thread-local static variable, the application declares the variable as follows to ensure that every thread has its own private copy:

```c
__declspec(thread) DWORD cur_pos = 0;
```

```c
// reserve a slot for a variable
DWORD var_index = TlsAlloc();

// set it to the value 10
TlsSetValue(var_index, 10);

// get the value
int var = TlsGetValue(var_index);

// release the index
TlsFree(var_index);
```

*Figure 22.16 Code for dynamic thread-local storage.*

# Summary

Microsoft designed Windows XP to be an extensible, portable operating system—one able to take advantage of new techniques and hardware. Windows XP supports multiple operating environments and symmetric multiprocessing, including both 32-bit and 64-bit processors and NUMA computers. The use of kernel objects to provide basic services, along with support for client-server computing, enables Windows XP to support a wide variety of application environments. For instance, Windows XP can run programs compiled for MS-DOS, Windows16, Windows 95, Windows XP, and POSIX. It provides virtual memory, integrated caching, and preemptive scheduling. Windows XP supports a security model stronger than those of previous Microsoft operating systems and includes internationalization features. Windows XP runs on a wide variety of computers, so users can choose and upgrade hardware to match their budgets and performance requirements without needing to alter the applications they run.

# Exercises

22.1. Under what circumstances would one use the deferred procedure calls facility in Windows XP?

22.2. What is a handle, and how does a process obtain a handle?

22.3. Describe the management scheme of the virtual memory manager. How does the VM manager improve performance?

22.4. Describe a useful application of the no-access page facility provided in Windows XP.

22.5. The IA64 processors contain registers that can be used to address a 64-bit address space. However, Windows XP limits the address space of user programs to 8 TB, which corresponds to 43 bits' worth. Why was this decision made?

22.6. Describe the three techniques used for communicating data in a local procedure call. What different settings are most conducive to the application of the different message-passing techniques?

22.7. What manages cache in Windows XP? How is cache managed?

22.8. What is the purpose of the Win16 execution environment? What limitations are imposed on the programs executing inside this environment? What are the protection guarantees provided between different applications executing inside the Windows16 environment? What are the protection guarantees provided between an application executing inside the Windows16 environment and a 32-bit application?

22.9. Describe two user-mode processes that Windows XP provides to enable it to run programs developed for other operating systems.

22.10. How does the NTFS directory structure differ from the directory structure used in Unix operating systems?

22.11. What is a process, and how is it managed in Windows XP?

22.12. What is the fiber abstraction provided by Windows XP? How does it differ from the threads abstraction?

# Bibliographical Notes

Solomon and Russinovich [2000] give an overview of Windows XP and considerable technical detail about system internals and components. Tate [2000] is a good reference on using Windows XP. The Microsoft Windows XP Server Resource Kit (Microsoft [2000b]) is a six-volume set helpful for using and deploying Windows XP. The Microsoft Developer Network Library (Microsoft [2000a]), issued quarterly, supplies a wealth of information on Windows XP and other Microsoft products.

Iseminger [2000] provides a good reference on the Windows XP Active Directory. Richter [1997] gives a detailed discussion on writing programs that use the Win32 API. Silberschatz et al. [2001] contains a good discussion of B+ trees.
```