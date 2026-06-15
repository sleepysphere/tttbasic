For the sake of simplicity, assume that the human always starts first.

In this case, "multi-user" means that several users can play simultaneously against the computer.

Simply "extends" your previous exercise ("Client-server, terminal-based human-computer, basic Tic-Tac-Toe") by  using threads to support multiple users playing at the same time against the computer (one thread for each game).

First, do not use a thread pool. Check that 4 users can play at the same time. Check also (use a bash script) that if 10,000 users try to connect at the same time, your server will crash. Be careful: your computer may also freeze.
Secondly, use a thread pool (with 4 threads). Check that 4 users can play at the same time, but not 5. Check also (use the same bash script as before) that if 10,000 users try to connect at the same time, your server will not crash.