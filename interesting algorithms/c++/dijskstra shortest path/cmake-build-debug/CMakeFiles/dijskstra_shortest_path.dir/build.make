# CMAKE generated file: DO NOT EDIT!
# Generated by "Unix Makefiles" Generator, CMake Version 3.15

# Delete rule output on recipe failure.
.DELETE_ON_ERROR:


#=============================================================================
# Special targets provided by cmake.

# Disable implicit rules so canonical targets will work.
.SUFFIXES:


# Remove some rules from gmake that .SUFFIXES does not remove.
SUFFIXES =

.SUFFIXES: .hpux_make_needs_suffix_list


# Suppress display of executed commands.
$(VERBOSE).SILENT:


# A target that is always out of date.
cmake_force:

.PHONY : cmake_force

#=============================================================================
# Set environment variables for the build.

# The shell in which to execute make rules.
SHELL = /bin/sh

# The CMake executable.
CMAKE_COMMAND = /opt/clion-2019.3.5/bin/cmake/linux/bin/cmake

# The command to remove a file.
RM = /opt/clion-2019.3.5/bin/cmake/linux/bin/cmake -E remove -f

# Escaping for special characters.
EQUALS = =

# The top-level source directory on which CMake was run.
CMAKE_SOURCE_DIR = "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path"

# The top-level build directory on which CMake was run.
CMAKE_BINARY_DIR = "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/cmake-build-debug"

# Include any dependencies generated for this target.
include CMakeFiles/dijskstra_shortest_path.dir/depend.make

# Include the progress variables for this target.
include CMakeFiles/dijskstra_shortest_path.dir/progress.make

# Include the compile flags for this target's objects.
include CMakeFiles/dijskstra_shortest_path.dir/flags.make

CMakeFiles/dijskstra_shortest_path.dir/main.cpp.o: CMakeFiles/dijskstra_shortest_path.dir/flags.make
CMakeFiles/dijskstra_shortest_path.dir/main.cpp.o: ../main.cpp
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green --progress-dir="/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/cmake-build-debug/CMakeFiles" --progress-num=$(CMAKE_PROGRESS_1) "Building CXX object CMakeFiles/dijskstra_shortest_path.dir/main.cpp.o"
	/usr/sbin/c++  $(CXX_DEFINES) $(CXX_INCLUDES) $(CXX_FLAGS) -o CMakeFiles/dijskstra_shortest_path.dir/main.cpp.o -c "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/main.cpp"

CMakeFiles/dijskstra_shortest_path.dir/main.cpp.i: cmake_force
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green "Preprocessing CXX source to CMakeFiles/dijskstra_shortest_path.dir/main.cpp.i"
	/usr/sbin/c++ $(CXX_DEFINES) $(CXX_INCLUDES) $(CXX_FLAGS) -E "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/main.cpp" > CMakeFiles/dijskstra_shortest_path.dir/main.cpp.i

CMakeFiles/dijskstra_shortest_path.dir/main.cpp.s: cmake_force
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green "Compiling CXX source to assembly CMakeFiles/dijskstra_shortest_path.dir/main.cpp.s"
	/usr/sbin/c++ $(CXX_DEFINES) $(CXX_INCLUDES) $(CXX_FLAGS) -S "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/main.cpp" -o CMakeFiles/dijskstra_shortest_path.dir/main.cpp.s

# Object files for target dijskstra_shortest_path
dijskstra_shortest_path_OBJECTS = \
"CMakeFiles/dijskstra_shortest_path.dir/main.cpp.o"

# External object files for target dijskstra_shortest_path
dijskstra_shortest_path_EXTERNAL_OBJECTS =

dijskstra_shortest_path: CMakeFiles/dijskstra_shortest_path.dir/main.cpp.o
dijskstra_shortest_path: CMakeFiles/dijskstra_shortest_path.dir/build.make
dijskstra_shortest_path: CMakeFiles/dijskstra_shortest_path.dir/link.txt
	@$(CMAKE_COMMAND) -E cmake_echo_color --switch=$(COLOR) --green --bold --progress-dir="/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/cmake-build-debug/CMakeFiles" --progress-num=$(CMAKE_PROGRESS_2) "Linking CXX executable dijskstra_shortest_path"
	$(CMAKE_COMMAND) -E cmake_link_script CMakeFiles/dijskstra_shortest_path.dir/link.txt --verbose=$(VERBOSE)

# Rule to build all files generated by this target.
CMakeFiles/dijskstra_shortest_path.dir/build: dijskstra_shortest_path

.PHONY : CMakeFiles/dijskstra_shortest_path.dir/build

CMakeFiles/dijskstra_shortest_path.dir/clean:
	$(CMAKE_COMMAND) -P CMakeFiles/dijskstra_shortest_path.dir/cmake_clean.cmake
.PHONY : CMakeFiles/dijskstra_shortest_path.dir/clean

CMakeFiles/dijskstra_shortest_path.dir/depend:
	cd "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/cmake-build-debug" && $(CMAKE_COMMAND) -E cmake_depends "Unix Makefiles" "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path" "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path" "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/cmake-build-debug" "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/cmake-build-debug" "/home/tomas/staticData/github/playzone/interesting algorithms/c++/dijskstra shortest path/cmake-build-debug/CMakeFiles/dijskstra_shortest_path.dir/DependInfo.cmake" --color=$(COLOR)
.PHONY : CMakeFiles/dijskstra_shortest_path.dir/depend

