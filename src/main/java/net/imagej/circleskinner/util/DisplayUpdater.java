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