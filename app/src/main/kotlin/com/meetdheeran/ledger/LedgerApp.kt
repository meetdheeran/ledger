package com.meetdheeran.ledger

import android.app.Application

/**
 * Nothing to set up at start beyond existing: the database opens lazily and the
 * rules file is read on first parse. Kept as a named Application class so there
 * is an obvious place for anything that later genuinely needs process scope.
 */
class LedgerApp : Application()
