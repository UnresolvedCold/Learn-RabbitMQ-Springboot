package codes.shubham.emacsscheduler.scheduler.cost;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import codes.shubham.emacsscheduler.scheduler.pojo.ItemType;
import codes.shubham.emacsscheduler.scheduler.domain.TodoItem;
import org.joda.time.DateTime;

import java.time.LocalTime;
import java.util.*;

import static ai.timefold.solver.core.api.score.stream.Joiners.overlapping;

public class ScheduleConstraints implements ConstraintProvider {

    public static LocalTime dayStartTime = LocalTime.of(9, 0, 0);
    public static LocalTime dayEndTime = LocalTime.of(22, 0, 0);
    public static LocalTime workStartTime = LocalTime.of(11, 0, 0);
    public static LocalTime workEndTime = LocalTime.of(19, 0, 0);
    public static LocalTime currentTime = LocalTime.now();

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        List<Constraint> constraints = new ArrayList<>();
        constraints.add(overlappingTime(constraintFactory));
        constraints.add(generateSchedulesWithinDayActiveHours(constraintFactory));
        constraints.add(createNewSchedulesAfterCurrentTime(constraintFactory));

        constraints.add(preferBreaksBetweenTasks(constraintFactory));
        constraints.add(scheduleTaskBeforeDeadline(constraintFactory));
        constraints.add(preferHighPriorityTasksFirst(constraintFactory));

        // if not holidays
        Set<Integer> workingDays = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5));
        if (workingDays.contains(DateTime.now().getDayOfWeek())) {
            constraints.add(preferWorkItemsInWorkHours(constraintFactory));
            constraints.add(preferPersonalItemsInNonWorkingHours(constraintFactory));
        }

        return constraints.toArray(new Constraint[0]);
    }

    private Constraint preferHighPriorityTasksFirst(ConstraintFactory constraintFactory) {
        // for each unique pair
        // compare the start times
        // If high priority task is starting after low priority task -> penalize
        return constraintFactory.forEachUniquePair(TodoItem.class)
                .filter((item1, item2) -> item1.getPriority().compareTo(item2.getPriority()) < 0)
                .filter((item1, item2) -> item1.getStartTime().isAfter(item2.getStartTime()))
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("prefer high priority tasks first");
    }

    private Constraint scheduleTaskBeforeDeadline(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TodoItem.class)
                .filter(item -> item.getDeadline().isBefore(item.getEndTime()))
                .filter(item -> !item.isPinned())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("schedule task before deadline");
    }

    // break of 15 mins between tasks
    private Constraint preferBreaksBetweenTasks(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachUniquePair(TodoItem.class,
                        overlapping(TodoItem::getStartTime, TodoItem::getEndTimeWithBuffer))
                .filter((item1, item2) -> item1.getStartTime().isBefore(item2.getEndTimeWithBuffer()))
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("prefer breaks between tasks");
    }

    private Constraint createNewSchedulesAfterCurrentTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TodoItem.class)
                .filter(item -> item.getStartTime().isBefore(currentTime))
                .filter(item -> !item.isPinned())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("create new schedules after current time");
    }

    private Constraint generateSchedulesWithinDayActiveHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TodoItem.class)
                .filter(item -> item.getStartTime().isBefore(dayStartTime)
                        || item.getEndTime().isAfter(dayEndTime))
                .filter(item -> !item.isPinned())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("generate schedules within day active hours");
    }

    private Constraint preferPersonalItemsInNonWorkingHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TodoItem.class)
                .filter(item -> item.getItemType() == ItemType.PERSONAL)
                .filter(item ->
                        item.getStartTime().isAfter(workStartTime)
                        && item.getEndTime().isBefore(workEndTime))
                .filter(item -> !item.isPinned())
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("prefer personal items in non-working hours");
    }

    private Constraint preferWorkItemsInWorkHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(TodoItem.class)
                .filter(item -> item.getItemType() == ItemType.WORK)
                .filter(item ->
                        item.getStartTime().isBefore(workStartTime)
                        || item.getEndTime().isAfter(workEndTime))
                .filter(item -> !item.isPinned())
                .penalize(HardSoftScore.ONE_SOFT)
                .asConstraint("prefer work items in work hours");
    }

    private Constraint overlappingTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachUniquePair(TodoItem.class,
                        overlapping(TodoItem::getStartTime, TodoItem::getEndTime))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("overlapping time");
    }
}
