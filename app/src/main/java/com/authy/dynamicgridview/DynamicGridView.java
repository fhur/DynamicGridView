package com.authy.dynamicgridview;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListAdapter;

/**
 * Created by fernandinho on 10/29/14.
 */
public class DynamicGridView extends GridView implements SwapHistory.Swapper, AdapterView.OnItemLongClickListener{

    public static final String TAG = "DynamicGridView";

    public final static float SCROLL_BOUND_UP = 0.20f;
    public final static float SCROLL_BOUND_DOWN = 0.80f;
    public final static int SCROLL_SPEED = 8;

    private DragView dragView;

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

    private int scrollSpeed;

    private OnDragListener onDragListener;
    private OnDropListener onDropListener;

    public DynamicGridView(Context context) {
        super(context);
        init();
    }

    public DynamicGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public void init(){
        onDragListener = new DefOnDragListener();
        onDropListener = new DefOnDropListener();
        scrollSpeed = (int)(SCROLL_SPEED * getResources().getDisplayMetrics().density + 0.5f);
        setOnItemLongClickListener(this);
    }

    /**
     * Equivalent to {@link #setAdapter(android.widget.ListAdapter)}
     */
    public void setAdapter(DynamicGridAdapter<?> adapter) {
        this.setAdapter((ListAdapter)adapter);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        lastMotionEvent = event;
        int action = event.getAction() & MotionEvent.ACTION_MASK;

        if(dragging && action == MotionEvent.ACTION_UP){
            dragging = false;
            stopDragging(dragStartPos, event);
            return true;
        }
        else if(dragging && action == MotionEvent.ACTION_MOVE){
            updateDragView(event, dragging);
            return true;
        }

        return super.onTouchEvent(event);
    }


    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

        startDragging(position, view, lastMotionEvent);
        return true;
    }

    /**
     * Starts dragging a view at the given position
     *
     * @param pos the position of that view in the grid
     * @param v the view that is being dragged around
     * @param event the motion event that triggered the drag
     */
    public void startDragging(int pos, View v, MotionEvent event){
        log("Start drag at %.2f,%.2f", event.getX(), event.getY());

        dragging = true;        // set the drag flag to true
        dragStartPos = pos;     // set the position where the drag started
        dragStartEvent = event; // set the event that started the drag
        lastOverlayedPos = pos; // update the last overlayed position with the initial drag position

        hide(v);

        dragView = new DragView(getContext(), v);
        dragView.show(v.getWindowToken(), (int)event.getRawX(), (int)event.getRawY());

        // call the drag listener => notify that a drag operation has started
        onDragListener.onDragStarted(pos);
    }

    /**
     * Stops the current drag event. The fundamental difference between {@code stopDragging} and
     * {@link #cancelDragging(int, android.view.MotionEvent) cancelDragging} is that
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
            cancelDragging(initialPosition, event);
        }
        else{
            View view = getChildAt(finalPosition - getFirstVisiblePosition());
            if(view != null){
                view.setVisibility(View.VISIBLE);
            }

            // do not call swap here as the swap is already done in updateDragView
            // notify the listener => an item has been dropped
            onDropListener.onDrop(initialPosition, finalPosition);
        }
    }

    private void hide(View v){
        if(v != null) v.setVisibility(INVISIBLE);
    }

    /**
     * Cancels the current drag operation returning all dragged elements to their original place.
     *
     * @param originatingPosition the position in the adapter where the drag operation was started
     * @param event the motion event that was fired when the drag operation occured
     */
    public void cancelDragging(int originatingPosition, MotionEvent event){
        log("Cancelled drag operation originated at " + originatingPosition);
        View view = getChildAt(dragStartPos);
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

            View view = getChildAt(currentOverlayedPos - getFirstVisiblePosition());
            hide(view);

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
        smoothScrollBy(scrollDirection.getDirection() * scrollSpeed, 0);
        return scrollDirection;
    }

    /**
     * Swaps the position of two items in the adapter
     * @param pos1 the first item's position in the adapter
     * @param pos2 the second item's position in the adapter
     */
    @Override
    public void swapItems(int pos1, int pos2){
        getAdapter().swap(pos1, pos2);
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

        float topBound = getHeight()*SCROLL_BOUND_UP;
        float bottomBound = getHeight()*SCROLL_BOUND_DOWN;

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

        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    return getFirstVisiblePosition() + i;
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
        return getChildAt(index);
    }

    @Override
    public DynamicGridAdapter<?> getAdapter() {
        return (DynamicGridAdapter)super.getAdapter();
    }

    /**
     * @return the last event captured by the {@link #onTouchEvent(android.view.MotionEvent)}
     */
    public MotionEvent getLastMotionEvent() {
        return lastMotionEvent;
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
