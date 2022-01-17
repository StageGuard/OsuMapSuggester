package me.stageguard.obms.script

class ScriptTimeoutException(val limit: Long) :
    RuntimeException("Script execution timeout waiting for $limit ms.")