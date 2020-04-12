import sys, utils

"""
Schedule the jobs by weighted completion times (ie. from least first)
weighted completion time = sum of wct of prior jobs + wct of this job (wct = w * l)

Runs twice, once with the naive algo to compute sched by difference: w - l, and then by lower ration: w / l
"""
def submitDataFile(hint, fname):
    submitDataArray(hint, fname, utils.readInputFile(fname))

def submitDataArray(hint, source, lines):
    nums = []
    for l in lines:
        j = map(int, l.split())
        nums.append(j)
        
    # first line is an integer num of jobs (lines to follow), the rest are pairs of integers weight, length
    print '  source \'%s\', #jobs=%d, hint=%s' % (source, nums[0][0], hint)

    # put them in a queue and sort them
    diffList = []
    for w,l in nums[1:]: # ignore the first
        diff = w - l
        diffList.append((diff, w, l))

    diffList.sort(cmp=byDiff)
    q = utils.Queue()
    for item in diffList: q.push(item)

    printResults('difference', q)

    diffList = []
    for w,l in nums[1:]: # ignore the first
        l = l * 1.0
        diff = w / l
        diffList.append((diff, w, l))

    diffList.sort(cmp=byDiff)
    q = utils.Queue()
    for item in diffList: q.push(item)

    printResults('ratio', q)

def byDiff(entry1, entry2):
    if entry1[0] > entry2[0]:
        return -1
    elif entry1[0] < entry2[0]:
        return 1
    else: # need to compare the weights
        return entry2[1] - entry1[1]

def printResults(version, q):
    ct = 0  # completion time
    wct = 0 # weighted
    
    if utils.Debug.debug: print '    version %s' % version
    
    while not q.isEmpty():
        diff,w,l = q.pop()
        ct += l
        wct += w * ct  
        if utils.Debug.debug: print '        popped job, diff=%d, w=%d, l=%d, wct=%d' % (diff, w, l, wct)
        
    print '        %s, result=%d' % (version, wct)
        
if __name__ == '__main__':
    if len(sys.argv) == 2:
        if sys.argv[1] == 'debug':
            utils.Debug.debug = True
        elif sys.argv[1] != 'real':
            print('usage: real')
            sys.exit()    
        submitDataFile('no hint', 'w1_asgn1_jobs.txt')
    else:
        #utils.Debug.debug = True
        submitDataArray('expected result: Ratio: 22, difference: 23', 'video', ['2','3 5','1 2'])
        submitDataArray('expected result: Ratio: 31814, difference: 31814', 'test1', ['6','8 50','74 59','31 73','45 79','10 10','41 66'])
        submitDataArray('expected result: Ratio: 60213, difference: 61545', 'test2', ['10','8 50','74 59','31 73','45 79','24 10','41 66','93 43','88 4','28 30','41 13'])
        submitDataArray('expected result: Ratio: 674634, difference: 688647', 'test3', ['32','1 37','79 39','94 16','16 73','48 44','52 40','96 27','15 86','20 81','99 57','10 90','46 66','77 52','42 74','16 45','47 4','84 41','34 54','87 53','13 69','83 88','69 63','5 97','13 65','10 46','17 10','62 79','62 32','13 12','57 61','100 98','43 7'])
        #submitDataFile('expected result:6', 'testcase1.txt')
