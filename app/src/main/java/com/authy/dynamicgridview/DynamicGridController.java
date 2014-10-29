package com.authy.dynamicgridview;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import static android.widget.AdapterView.INVALID_POSITION;

/**
 * Created by fernandinho on 10/27/14.
 */
public class DynamicGridController implements AdapterView.OnItemLongClickListener, View.OnTouchListener, SwapHistory.Swapper{

    public final static float SCROLL_BOUND_UP = 0.10f;
    public final static float SCROLL_BOUND_DOWN = 0.90f;

    public enum ScrollDirection { up, down, none }

    private final static String TAG = "DynamicGridController";

    private Context context;
    private GridView gridView;
    private DragView dragView;
    private DynamicGridAdapter<?> adapter;

    /**
     * The last MotionEvent captured by the onTouch
     */
    private MotionEvent lastMotionEvent;

    /**
     * The MotionEvent that was fired when the drag operation started
     */
    private MotionEvent dragStartEvent;

    /**
     * a flag that indicates if the user is currently dragging or not
     */
    private boolean dragging;

    /**
     * The position in the adapter where the drag operation was started
     */
    private int dragStartPos;

    private int lastOverlayedPos;

    private OnDragListener onDragListener;
    private OnDropListener onDropListener;

    public DynamicGridController(GridView gridView, DynamicGridAdapter<?> adapter) {
        this.adapter = adapter;
        this.context = gridView.getContext();
        this.gridView = gridView;
        this.gridView.setOnItemLongClickListener(this);
        this.gridView.setOnTouchListener(this);

        this.onDragListener = new DefOnDragListener();
        this.onDropListener = new DefOnDropListener();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        lastMotionEvent = event;

        int action = event.getAction() & MotionEvent.ACTION_MASK;

        if(dragging && action == MotionEvent.ACTION_UP){
            dragging = false;
            stopDragging(dragStartPos, event);
        }
        else if(dragging && action == MotionEvent.ACTION_MOVE){
            updateDragView(event, dragging);
            return false;
        }

        return gridView.onTouchEvent(event);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

        lastOverlayedPos = position;
        startDragAt(position, view, lastMotionEvent);

        return true;
    }

    /**
     * Starts dragging a view at the given position
     *
     * @param pos the position of that view in the grid
     * @param v the view that is being dragged around
     * @param event the motion event that triggered the drag
     */
    public void startDragAt(int pos, View v, MotionEvent event){
        Log.d(TAG, "Start drag at " + event.getX() + "," + event.getY());

        dragging = true;
        dragStartPos = pos;
        dragStartEvent = event;

        v.setVisibility(View.INVISIBLE);

        dragView = new DragView(context, v);
        dragView.show(v.getWindowToken(), (int)event.getRawX(), (int)event.getRawY());

        // call the drag listener => notify that a drag operation has started
        onDragListener.onDragStarted(pos);
    }

    /**
     * Stops the current drag event. The fundamental difference between {@code stopDragging} and
     * {@link #cancelDragOperation(int, android.view.MotionEvent) cancelDragging} is that
     * {@code stopDragging}'s changes should be persisted while {@code cancelDragging}'s
     * changes should not (because the operation was cancelled
     *
     * @param initialPosition the position where the drag operation was started
     * @param event a motion event
     */
    public void stopDragging(int initialPosition, MotionEvent event){
        dragView.remove();
        int finalPosition = findClosestViewIndex(event);
        if( finalPosition == INVALID_POSITION){
            cancelDragOperation(initialPosition, event);
        }
        else{
            View view = gridView.getChildAt(finalPosition);
            if(view != null){
                view.setVisibility(View.VISIBLE);
            }

            // do not call swap here as the swap is already done in updateDragView
            // notify the listener => an item has been dropped
            onDropListener.onDrop(initialPosition, finalPosition);
        }
    }

    /**
     * Cancels the current drag operation returning all dragged elements to their original place.
     *
     * @param originatingPosition the position in the adapter where the drag operation was started
     * @param event the motion event that was fired when the drag operation occured
     */
    public void cancelDragOperation(int originatingPosition, MotionEvent event){
        log("Cancelled drag operation originated at "+originatingPosition);
        View view = gridView.getChildAt(dragStartPos);
        view.setVisibility(View.VISIBLE);
        dragging = false;
    }

    /**
     * Called when the users is moving the dragged view around
     *
     * @param event a motion event
     * @param dragging if the user is dragging
     */
    public void updateDragView(MotionEvent event, boolean dragging){
        if(dragging){
            // update the position of the dragView with the event's x and y coordinates
            dragView.move((int)event.getRawX(), (int)event.getRawY());
            // check
            scrollIfNeeded(event);

            // obtain the position that is being overlayed by the dragView and check
            // if it has changed. If a change has indeed occurred swap the items.
            int currentOverlayedPos = findClosestViewIndex(event);
            if(currentOverlayedPos != INVALID_POSITION && currentOverlayedPos != lastOverlayedPos) {
                swapItems(lastOverlayedPos, currentOverlayedPos);
                lastOverlayedPos = currentOverlayedPos;
            }

            // call listeners
            onDragListener.onDragged(dragStartPos, dragStartEvent, event);
        }
    }

    /**
     * Given a motion event, this method checks if the event's location is close enough to the
     * upper or lower border and updates the gridView's scroll accordingly.
     * @param event a motion event
     * @return the scroll direction
     */
    public ScrollDirection scrollIfNeeded(MotionEvent event){
        ScrollDirection scrollDirection = getScrollDirection(event);

        if(scrollDirection == ScrollDirection.up){
            gridView.smoothScrollBy(-5, 1);
        }
        else if (scrollDirection == ScrollDirection.down){
            gridView.smoothScrollBy(+5, 1);
        }

        return scrollDirection;
    }

    /**
     * Swaps the position of two items in the adapter
     * @param pos1 the first item's position in the adapter
     * @param pos2 the second item's position in the adapter
     */
    @Override
    public void swapItems(int pos1, int pos2){

        adapter.swap(pos1, pos2);
    }

    /**
     * @param event a motion event
     * @return returns the direction where the grid should scroll to or none if
     * no scrolling should be computed
     */
    public ScrollDirection getScrollDirection(MotionEvent event) {
        // this method calculates the Y position of the event.
        // if the Y position is in the top 10%, scroll up
        // if the Y position is in the bottom 10%, scroll down
        // else => none
        float eY = event.getY();

        float topBound = gridView.getHeight()*SCROLL_BOUND_UP;
        float bottomBound = gridView.getHeight()*SCROLL_BOUND_DOWN;

        if(eY <= topBound){
            return ScrollDirection.up;
        }
        else if(eY >= bottomBound){
            return ScrollDirection.down;
        }
        else {
            return ScrollDirection.none;
        }
    }

    /**
     * @param event a motion event
     * @return Given a motion event, returns the index of the closest view in the grid to that
     * event. You can then use {@link android.widget.GridView#getChildAt(int)} to obtain the view
     */
    public int findClosestViewIndex(MotionEvent event){
        int x = (int) event.getX();
        int y = (int) event.getY();

        Rect frame = new Rect();

        final int count = gridView.getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = gridView.getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    return gridView.getFirstVisiblePosition() + i;
                }
            }
        }
        return INVALID_POSITION;
    }

    /**
     * Returns the view closest to a given event or null if not found
     */
    public View findClosestView(MotionEvent event){
        int index = findClosestViewIndex(event);
        return gridView.getChildAt(index);
    }

    private static void log(String format, Object... args){
        String formatted = String.format(format, args);
        Log.d(TAG, formatted);
    }

    public interface OnDragListener {

        /**
         * Called when a drag operation is started
         *
         * @param pos the position where the drag operation was started
         */
        public void onDragStarted(int pos);

        /**
         * This method is called while the user is dragging the view around
         *
         * @param initalPosition the position in the adapter where the drag operation was started
         * @param initial the initial motion event that triggered the drag operation
         * @param current the current motion event
         */
        public void onDragged(int initalPosition, MotionEvent initial, MotionEvent current);
    }

    public interface OnDropListener {

        /**
         * Called when a dragged item is dropped
         *
         * @param from the original position where the item drag was started
         * @param to the final position where the item was dropped
         */
        public void onDrop(int from, int to);
    }

    /**
     * Implementation of OnDragListener that simply logs the methods
     */
    public static class DefOnDragListener implements OnDragListener{
        @Override
        public void onDragStarted(int pos) {
            log("Drag started at %d ", pos);
        }

        @Override
        public void onDragged(int pos, MotionEvent initial, MotionEvent current) {
            log("Dragging from %d to (%.2f, %.2f)", pos, current.getX(), current.getY());
        }
    }

    /**
     * Implementation of OnDropListener that simply logs the methods
     */
    public static class DefOnDropListener implements OnDropListener {
        @Override
        public void onDrop(int from, int to) {
            log("Dropped from %d to %d", from, to);
        }
    }


}
