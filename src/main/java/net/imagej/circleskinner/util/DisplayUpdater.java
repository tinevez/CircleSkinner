/*-
 * #%L
 * A Fiji plugin for the automated detection and quantification of circular structure in images.
 * %%
 * Copyright (C) 2016 - 2022 My Company, Inc.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package net.imagej.circleskinner.util;

/**
 * This is a helper class modified after a class by Albert Cardona
 */
public abstract class DisplayUpdater extends Thread
{
	long request = 0;

	// Constructor autostarts thread
	public DisplayUpdater()
	{
		super( "Display updater thread" );
		setPriority( Thread.NORM_PRIORITY );
		start();
	}

	public void doUpdate()
	{
		if ( isInterrupted() )
			return;
		synchronized ( this )
		{
			request++;
			notify();
		}
	}

	public void quit()
	{
		interrupt();
		synchronized ( this )
		{
			notify();
		}
	}

	@Override
	public void run()
	{
		while ( !isInterrupted() )
		{
			try
			{
				final long r;
				synchronized ( this )
				{
					r = request;
				}
				// Call displayer update from this thread
				if ( r > 0 )
					refresh();
				synchronized ( this )
				{
					if ( r == request )
					{
						request = 0; // reset
						wait();
					}
					// else loop through to update again
				}
			}
			catch ( final Exception e )
			{
				e.printStackTrace();
			}
		}
	}

	public abstract void refresh();
}
